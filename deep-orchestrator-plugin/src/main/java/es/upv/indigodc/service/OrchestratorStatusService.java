package es.upv.indigodc.service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.dao.model.GetMultipleDataResult;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.security.model.User;
import alien4cloud.security.users.UserService;
import es.upv.indigodc.Util;
import es.upv.indigodc.configuration.CloudConfiguration;
import es.upv.indigodc.configuration.CloudConfigurationManager;
import es.upv.indigodc.service.model.PluginDeploymentStatus;
import es.upv.indigodc.service.model.DeployedTopologyInformation;
import es.upv.indigodc.service.model.OrchestratorIamException;
import es.upv.indigodc.service.model.StatusNotFoundException;
import es.upv.indigodc.service.model.response.OrchestratorResponse;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.collect.Maps;

/**
 * Handle all deployment status request
 */
@Component
@Scope("prototype")
@Slf4j
public class OrchestratorStatusService {

  protected ScheduledExecutorService topologyDeploymentStatusThreadpool;

  protected ScheduledExecutorService instancesDeploymentStatusThreadpool;

  protected ScheduledFuture<?> topologyDeploymentStatusHandle;
  protected ScheduledFuture<?> instancesDeploymentStatusHandle;

  @Resource(name = "alien-monitor-es-dao")
  protected IGenericSearchDAO alienMonitorDao;

  @Autowired
  protected StatusManager<DeploymentStatus> topologyDeploymentStatusManager;

  @Autowired
  protected StatusManager<HashMap<String, HashMap<String, InstanceInformation>>> instancesDeploymentStatusManager;

  @Autowired
  protected UserService userService;

  @Autowired
  protected CloudConfigurationManager cloudConfigurationManager;

  @Autowired
  protected OrchestratorConnector orchestratorConnector;

  @AllArgsConstructor
  public static class ObtainStatusDeployment implements Runnable {

    protected OrchestratorConnector orchestratorConnector;

    protected StatusManager<DeploymentStatus> statusManager;

    protected CloudConfigurationManager cloudConfigurationManager;

    protected UserService userService;

    @Override
    public void run() {
      log.info("check status");
      final Collection<PluginDeploymentStatus<DeploymentStatus>> activeDeployments = statusManager
          .getActiveDeployments();
      for (final PluginDeploymentStatus<DeploymentStatus> dds : activeDeployments) {
        final CloudConfiguration configuration = cloudConfigurationManager
            .getCloudConfiguration(dds.getOrchestratorId());
        try {
          User user = userService.retrieveUser(dds.getDeployerUsername());
          OrchestratorResponse response = orchestratorConnector.callDeploymentStatus(configuration, user.getUsername(),
              user.getPlainPassword(), dds.getOrchestratorDeploymentUuid());
          String statusTopologyDeployment = response.getStatusTopologyDeployment();
          statusManager.updateStatusByOrchestratorDeploymentUuid(dds.getOrchestratorDeploymentUuid(), null,
              Util.indigoDcStatusToDeploymentStatus(statusTopologyDeployment.toUpperCase()));

        } catch (NoSuchFieldException | IOException | StatusNotFoundException er) {
          log.error("Error getStatus", er);
          statusManager.updateStatusByOrchestratorDeploymentUuid(dds.getOrchestratorDeploymentUuid(), er,
              DeploymentStatus.UNKNOWN);
        } catch (OrchestratorIamException er) {
          switch (er.getHttpCode()) {
          case 404:
            statusManager.updateStatusByOrchestratorDeploymentUuid(dds.getOrchestratorDeploymentUuid(), null,
                DeploymentStatus.UNDEPLOYED);
            break;
          default:
            statusManager.updateStatusByOrchestratorDeploymentUuid(dds.getOrchestratorDeploymentUuid(), er,
                DeploymentStatus.UNKNOWN);
          }
          log.error("Error deployment ", er);
        } catch (Throwable er) {
          log.error("Error getStatus", er);
          statusManager.updateStatusByOrchestratorDeploymentUuid(dds.getOrchestratorDeploymentUuid(), er,
              DeploymentStatus.UNKNOWN);
        }
      }
    }
  }

  @AllArgsConstructor
  public static class ObtainStatusDeploymentNodes implements Runnable {

    protected OrchestratorConnector orchestratorConnector;

    protected StatusManager<HashMap<String, HashMap<String, InstanceInformation>>> statusManager;

    protected CloudConfigurationManager cloudConfigurationManager;

    protected User user;

    @Override
    public void run() {
      final Collection<PluginDeploymentStatus<HashMap<String, HashMap<String, InstanceInformation>>>> activeDeployments = statusManager
          .getActiveDeployments();
      for (final PluginDeploymentStatus<HashMap<String, HashMap<String, InstanceInformation>>> dds : activeDeployments) {
        log.info("call getInstancesInformation");
        String a4cDeploymentId = dds.getA4cDeploymentId();

        // deploymentContext.getDeploymentTopology().get
        final Map<String, Map<String, InstanceInformation>> topologyInfo = new HashMap<>();
        final Map<String, String> runtimeProps = new HashMap<>();
        final Map<String, InstanceInformation> instancesInfo = new HashMap<>();
        final String groupId = dds.getA4cDeploymentPaasId();
        // final String
        final PluginDeploymentStatus<HashMap<String, HashMap<String, InstanceInformation>>> orchestratorDeploymentMapping = statusManager
            .getStatusByA4cDeploymentId(a4cDeploymentId);

        if (orchestratorDeploymentMapping != null) {
          final String orchestratorUuidDeployment = orchestratorDeploymentMapping.getOrchestratorDeploymentUuid();
          // .getDeploymentId();//.getDeploymentPaaSId();

          final CloudConfiguration configuration = cloudConfigurationManager
              .getCloudConfiguration(dds.getOrchestratorId());
          try {
            OrchestratorResponse response = orchestratorConnector.callDeploymentStatus(configuration,
                user.getUsername(), user.getPlainPassword(), orchestratorUuidDeployment);

            log.info(response.getResponse().toString());
            Util.InstanceStatusInfo instanceStatusInfo = Util
                .indigoDcStatusToInstanceStatus(response.getStatusTopologyDeployment().toUpperCase());

            // Map<String, String> outputs = new HashMap<>();
            // outputs.put("Compute_public_address", "none");
            // runtimeProps.put("Compute_public_address", "value");
            final InstanceInformation instanceInformation = new InstanceInformation(instanceStatusInfo.getState(),
                instanceStatusInfo.getInstanceStatus(), runtimeProps, runtimeProps,
                // outputs);
                response.getOutputs());
            instancesInfo.put(a4cDeploymentId, instanceInformation);
            topologyInfo.put(groupId, instancesInfo);
            // callback.onSuccess(topologyInfo);
          } catch (NoSuchFieldException er) {
            // callback.onFailure(er);
            log.error("Error getInstancesInformation", er);
          } catch (IOException er) {
            // callback.onFailure(er);
            log.error("Error getInstancesInformation", er);
          } catch (OrchestratorIamException er) {
            final InstanceInformation instanceInformation = new InstanceInformation("UNKNOWN", InstanceStatus.FAILURE,
                runtimeProps, runtimeProps, new HashMap<>());
            instancesInfo.put(a4cDeploymentId, instanceInformation);
            topologyInfo.put(a4cDeploymentId, instancesInfo);
            // callback.onSuccess(topologyInfo);
            instancesInfo.put(a4cDeploymentId, instanceInformation);
            topologyInfo.put(a4cDeploymentId, instancesInfo);
            switch (er.getHttpCode()) {
            case 404:
              // callback.onSuccess(topologyInfo);
              break;
            default:
              // callback.onFailure(er);
            }
            log.error("Error deployment ", er);
          } catch (StatusNotFoundException er) {
            // callback.onFailure(er);
            log.error("Error deployment ", er);
          }
        } else {
          final InstanceInformation instanceInformation = new InstanceInformation("UNKNOWN", InstanceStatus.FAILURE,
              runtimeProps, runtimeProps, new HashMap<>());
          instancesInfo.put(a4cDeploymentId, instanceInformation);
          topologyInfo.put(a4cDeploymentId, instancesInfo);
          // callback.onSuccess(topologyInfo);
        }
      }
    }
  }

//  public static class MyThreadPoolExecutor extends ScheduledThreadPoolExecutor {
//
//    public MyThreadPoolExecutor(int corePoolSize) {
//      super(corePoolSize);
//    }
//
//    @Override
//    public void afterExecute(Runnable r, Throwable t) {
//      super.afterExecute(r, t);
//      // If submit() method is called instead of execute()
//      if (t == null && r instanceof Future<?>) {
//        try {
//          Object result = ((Future<?>) r).get();
//        } catch (CancellationException e) {
//          t = e;
//        } catch (ExecutionException e) {
//          t = e.getCause();
//        } catch (InterruptedException e) {
//          Thread.currentThread().interrupt();
//        }
//      }
//      if (t != null) {
//        // Exception occurred
//        log.error("Uncaught exception is detected! " + t + " st: " + Arrays.toString(t.getStackTrace()));
//        // ... Handle the exception
//        // Restart the runnable again
//        execute(r);
//      }
//      // ... Perform cleanup actions
//    }
//  }

  public void init(Map<String, PaaSTopologyDeploymentContext> activeDeploymentContexts) {
    topologyDeploymentStatusThreadpool = //new MyThreadPoolExecutor(1);
      Executors.newScheduledThreadPool(1);
    instancesDeploymentStatusThreadpool = Executors.newScheduledThreadPool(1);
    for (Map.Entry<String, PaaSTopologyDeploymentContext> contextEntry : activeDeploymentContexts.entrySet()) {
      String a4cDeploymentPaaSId = contextEntry.getKey();
      // Try to retrieve the last deployment status event to initialize the cache
      Map<String, String[]> filters = Maps.newHashMap();
      filters.put("deploymentId", new String[] { contextEntry.getValue().getDeploymentId() });
      GetMultipleDataResult<PaaSDeploymentStatusMonitorEvent> lastEventResult = alienMonitorDao
          .search(PaaSDeploymentStatusMonitorEvent.class, null, filters, null, null, 0, 1, "date", true);
      QueryBuilder qb = QueryBuilders.termQuery("a4cDeploymentId", contextEntry.getValue().getDeploymentId());
      alienMonitorDao.customFind(DeployedTopologyInformation.class, qb);
      
      topologyDeploymentStatusManager.addDeepDeploymentStatus(contextEntry.getValue().getDeploymentPaaSId(),
          contextEntry.getValue().getDeploymentId(), null, contextEntry.getValue().getDeployment().getOrchestratorId(),
          contextEntry.getValue().getDeployment().getDeployerUsername(), null, DeploymentStatus.UNKNOWN);
    }
    topologyDeploymentStatusHandle = topologyDeploymentStatusThreadpool
        .scheduleWithFixedDelay(new ObtainStatusDeployment(orchestratorConnector, topologyDeploymentStatusManager,
            cloudConfigurationManager, userService), 0, 10, TimeUnit.SECONDS);
  }

  public void destroy() {
    topologyDeploymentStatusThreadpool.shutdownNow();
    instancesDeploymentStatusThreadpool.shutdownNow();
  }

  public void getStatus(PaaSDeploymentContext deploymentContext, IPaaSCallback<DeploymentStatus> callback) {
    String a4cDeploymentPaasId = deploymentContext.getDeploymentPaaSId();
    PluginDeploymentStatus<DeploymentStatus> dds = topologyDeploymentStatusManager
        .getStatusByA4cDeploymentPaasId(a4cDeploymentPaasId);
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

  public void getInstancesInformation(PaaSTopologyDeploymentContext deploymentContext,
      IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
    // String a4cDeploymentPaasId = deploymentContext.getDeploymentPaaSId();
    // PluginDeploymentStatus<Map<String, Map<String, InstanceInformation>>> dds =
    // instancesDeploymentStatusManager
    // .getStatusByA4cDeploymentPaasId(a4cDeploymentPaasId);
    // if (dds != null) {
    // if (dds.hasError()) {
    // callback.onFailure(dds.getError());
    // } else {
    // callback.onSuccess(dds.getStatus());
    // }
    // }
    //// else {
    //// callback.onSuccess(dds.getStatus());
    //// }
  }
}
