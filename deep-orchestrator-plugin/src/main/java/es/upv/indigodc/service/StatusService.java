package es.upv.indigodc.service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.dao.model.GetMultipleDataResult;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.utils.MapUtil;
import es.upv.indigodc.DateUtil;
import es.upv.indigodc.IndigoDcOrchestratorFactory;
import es.upv.indigodc.Util;
import es.upv.indigodc.configuration.CloudConfiguration;
import es.upv.indigodc.configuration.CloudConfigurationManager;
import es.upv.indigodc.model.Node;
import es.upv.indigodc.model.NodeInstance;
import es.upv.indigodc.model.NodeInstanceStatus;
import es.upv.indigodc.service.model.DeepDeploymentStatus;
import es.upv.indigodc.service.model.OrchestratorDeploymentMapping;
import es.upv.indigodc.service.model.OrchestratorIamException;
import es.upv.indigodc.service.model.OrchestratorResponse;
import es.upv.indigodc.service.model.StatusNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Handle all deployment status request
 */
@Component
@Slf4j
public class StatusService {

  protected ScheduledExecutorService threadpool;
  protected ScheduledFuture<?> statusHandle;

  @Resource(name = "alien-monitor-es-dao")
  protected IGenericSearchDAO alienMonitorDao;

  @Resource
  protected StatusManager statusManager;

  @Resource
  protected UserService userService;

  @Resource
  protected CloudConfigurationManager cloudConfigurationManager;

  public static class ObtainStatusDeployment implements Runnable {

    @Autowired
    protected OrchestratorConnector orchestratorConnector;

    protected StatusManager statusManager;

    protected CloudConfigurationManager cloudConfigurationManager;

    protected UserService userService;

    public ObtainStatusDeployment(StatusManager statusManager,
        CloudConfigurationManager cloudConfigurationManager, UserService userService) {
      this.statusManager = statusManager;
      this.cloudConfigurationManager = cloudConfigurationManager;
      this.userService = userService;
    }

    @Override
    public void run() {
      final Collection<DeepDeploymentStatus> activeDeployments =
          statusManager.getActiveDeployments();
      for (final DeepDeploymentStatus dds : activeDeployments) {
        final CloudConfiguration configuration =
            cloudConfigurationManager.getCloudConfiguration(dds.getOrchestratorId());
        try {
          OrchestratorResponse response = orchestratorConnector.callDeploymentStatus(configuration,
              userService.getCurrentUser().getUsername(),
              userService.getCurrentUser().getPlainPassword(), dds.getOrchestratorDeploymentUuid());
          String statusTopologyDeployment = response.getStatusTopologyDeployment();
          statusManager.updateStatusByOrchestratorDeploymentUuid(
              dds.getOrchestratorDeploymentUuid(), null,
              Util.indigoDcStatusToDeploymentStatus(statusTopologyDeployment.toUpperCase()));

        } catch (NoSuchFieldException er) {
          log.error("Error getStatus", er);
          statusManager.updateStatusByOrchestratorDeploymentUuid(
              dds.getOrchestratorDeploymentUuid(), er, DeploymentStatus.UNKNOWN);
        } catch (IOException er) {
          log.error("Error getStatus", er);
          statusManager.updateStatusByOrchestratorDeploymentUuid(
              dds.getOrchestratorDeploymentUuid(), er, DeploymentStatus.UNKNOWN);
        } catch (OrchestratorIamException er) {
          switch (er.getHttpCode()) {
            case 404:
              statusManager.updateStatusByOrchestratorDeploymentUuid(
                  dds.getOrchestratorDeploymentUuid(), null, DeploymentStatus.UNDEPLOYED);
              break;
            default:
              statusManager.updateStatusByOrchestratorDeploymentUuid(
                  dds.getOrchestratorDeploymentUuid(), er, DeploymentStatus.UNKNOWN);
          }
          log.error("Error deployment ", er);
        } catch (StatusNotFoundException er) {
          statusManager.updateStatusByOrchestratorDeploymentUuid(
              dds.getOrchestratorDeploymentUuid(), er, DeploymentStatus.UNKNOWN);
          er.printStackTrace();
        }
      }
    }


  }

  public void init(Map<String, PaaSTopologyDeploymentContext> activeDeploymentContexts) {
    threadpool = Executors.newScheduledThreadPool(1);
    for (Map.Entry<String, PaaSTopologyDeploymentContext> contextEntry : activeDeploymentContexts
        .entrySet()) {
      String a4cDeploymentPaaSId = contextEntry.getKey();
      // Try to retrieve the last deployment status event to initialize the cache
      Map<String, String[]> filters = Maps.newHashMap();
      filters.put("deploymentId", new String[] {contextEntry.getValue().getDeploymentId()});
      GetMultipleDataResult<PaaSDeploymentStatusMonitorEvent> lastEventResult =
          alienMonitorDao.search(PaaSDeploymentStatusMonitorEvent.class, null, filters, null, null,
              0, 1, "date", true);

    }
    ObtainStatusDeployment osd =
        new ObtainStatusDeployment(statusManager, cloudConfigurationManager, userService);
    statusHandle = threadpool.scheduleAtFixedRate(osd, 10, 10, TimeUnit.SECONDS);
  }

  public void destroy() {
    threadpool.shutdownNow();
  }

  public void getStatus(PaaSDeploymentContext deploymentContext,
      IPaaSCallback<DeploymentStatus> callback) {
    String a4cDeploymentPaasId = deploymentContext.getDeploymentPaaSId();// .getDeployment().getId();
    DeepDeploymentStatus dds = statusManager.getStatusByA4cDeploymentPaasId(a4cDeploymentPaasId);
    if (dds != null) {
      if (dds.hasError()) {
        callback.onFailure(dds.getError());
      } else {
        callback.onSuccess(dds.getStatus());
      }
    } else {
      callback.onSuccess(DeploymentStatus.UNKNOWN);
    }
  }



}
