package es.upv.indigodc;

import alien4cloud.dao.ESGenericSearchDAO;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.IOrchestratorPlugin;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.exception.MaintenanceModeException;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.security.model.User;
import alien4cloud.security.users.UserService;
import es.upv.indigodc.configuration.CloudConfiguration;
import es.upv.indigodc.configuration.CloudConfigurationManager;
import es.upv.indigodc.location.LocationConfiguratorFactory;
import es.upv.indigodc.service.BuilderService;
import es.upv.indigodc.service.OrchestratorConnector;
import es.upv.indigodc.service.StatusManager;
import es.upv.indigodc.service.OrchestratorStatusService;
import es.upv.indigodc.service.model.DeployedTopologyInformation;
import es.upv.indigodc.service.model.OrchestratorIamException;
import es.upv.indigodc.service.model.PluginDeploymentStatus;
import es.upv.indigodc.service.model.response.OrchestratorResponse;
import java.io.IOException;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;


import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

/**
 * Exposes the methods that allow the operations with the Orchestrator.
 *
 * @author asalic
 */
@Slf4j
@Component("orchestrator-plugin")
@Scope("prototype")
public class OrchestratorPlugin implements IOrchestratorPlugin<CloudConfiguration> {

  public static String TYPE = "DEEP";
  public static String ES_INDEX_NAME = "deep_es_index";

  /**
   * The configuration manager used to obtain the
   * {@link es.upv.indigodc.configuration.CloudConfiguration} instance that holds the parameters of
   * the plugin.
   */
  @Autowired
  @Qualifier("cloud-configuration-manager")
  private CloudConfigurationManager cloudConfigurationManager;

  /** The service that executes the HTTP(S) calls to the Orchestrator. */
  @Autowired
  @Qualifier("orchestrator-connector")
  private OrchestratorConnector orchestratorConnector;

  /**
   * The service that creates the payload (which includes the TOSCA topologies) that is sent to the
   * Orchestrator using {@link #orchestratorConnector}.
   */
  @Autowired
  @Qualifier("builder-service")
  private BuilderService builderService;

  /** Manages the instantiation of a new location configurator using a location type. */
  @Autowired
  private LocationConfiguratorFactory locationConfiguratorFactory;

  /** Manages the events produced by the Orchestrator. */
  @Autowired
  protected StatusManager<DeploymentStatus> topologyDeploymentStatusManager;
  
  @Autowired
  protected OrchestratorStatusService statusService;

  /** Manages the logged in user that executes this instance of service. */
  @Autowired
  private UserService userService;  

  @Resource(name = "alien-monitor-es-dao")
  protected ESGenericSearchDAO alienMonitorDao;

  @Override
  public void init(Map<String, PaaSTopologyDeploymentContext> activeDeployments) {
    if (activeDeployments != null) {
      statusService.init(activeDeployments);
    }
    alienMonitorDao.initIndices(ES_INDEX_NAME, null, DeployedTopologyInformation.class);
  }

  /**
   * Method called when this instance is scrapped.
   */
  public void destroy() {
    statusService.destroy();
  }

  @Override
  public void setConfiguration(String orchestratorId, CloudConfiguration configuration)
      throws PluginConfigurationException {
    if (configuration == null) {
      throw new PluginConfigurationException("Configuration must not be null");
    }
    cloudConfigurationManager.addCloudConfiguration(orchestratorId, configuration);
  }

  @Override
  public void deploy(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
    CloudConfiguration configuration = cloudConfigurationManager
        .getCloudConfiguration(deploymentContext.getDeployment().getOrchestratorId());
    final String a4cDeploymentPaasId = deploymentContext.getDeploymentPaaSId();
    final String a4cDeploymentId = deploymentContext.getDeployment().getId();
    String orchestratorUuidDeployment = null;
    User user = userService.retrieveUser(deploymentContext.getDeployment().getDeployerUsername());

    try {
      final String yamlPaasTopology =
          builderService.buildApp(deploymentContext, configuration.getImportIndigoCustomTypes());
      log.info(String.format("Deploying on orchestrator %s paas %s topology:\n%s", 
          a4cDeploymentPaasId,
          configuration.getOrchestratorEndpoint(),
          yamlPaasTopology));
      OrchestratorResponse response = orchestratorConnector.callDeploy(configuration,
          user.getUsername(),
          user.getPlainPassword(), yamlPaasTopology);
      orchestratorUuidDeployment = response.getOrchestratorUuidDeployment();
      topologyDeploymentStatusManager.addDeepDeploymentStatus(a4cDeploymentPaasId,
          a4cDeploymentId,
          orchestratorUuidDeployment,
          deploymentContext.getDeployment().getOrchestratorId(), user.getUsername(), null,
          DeploymentStatus.DEPLOYMENT_IN_PROGRESS);
      
      alienMonitorDao.save(new DeployedTopologyInformation(a4cDeploymentPaasId,
          a4cDeploymentId,
          orchestratorUuidDeployment,
          deploymentContext.getDeployment().getOrchestratorId(), user.getUsername()));
      // eventService.subscribe(configuration);
      callback.onSuccess(null);
    } catch (NoSuchFieldException er) {
      callback.onFailure(er);
      log.error("Error deployment", er);
      topologyDeploymentStatusManager.addDeepDeploymentStatus(a4cDeploymentPaasId,
          a4cDeploymentId,
          orchestratorUuidDeployment,
          deploymentContext.getDeployment().getOrchestratorId(), user.getUsername(), er,
          DeploymentStatus.FAILURE);
    } catch (IOException er) {
      callback.onFailure(er);
      log.error("Error deployment ", er);
      topologyDeploymentStatusManager.addDeepDeploymentStatus(a4cDeploymentPaasId,
          a4cDeploymentId,
          orchestratorUuidDeployment,
          deploymentContext.getDeployment().getOrchestratorId(), user.getUsername(), er,
          DeploymentStatus.FAILURE);
    } catch (OrchestratorIamException er) {
      callback.onFailure(er);
      log.error("Error deployment ", er);
      topologyDeploymentStatusManager.addDeepDeploymentStatus(a4cDeploymentPaasId,
          a4cDeploymentId,
          orchestratorUuidDeployment,
          deploymentContext.getDeployment().getOrchestratorId(), user.getUsername(), er,
          DeploymentStatus.FAILURE);
    }
  }

  @Override
  public ILocationConfiguratorPlugin getConfigurator(String locationType) {
    return locationConfiguratorFactory.newInstance(locationType);
  }

  @Override
  public void undeploy(PaaSDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
    final CloudConfiguration configuration = cloudConfigurationManager
        .getCloudConfiguration(deploymentContext.getDeployment().getOrchestratorId());
    final String a4cDeploymentPaasId = deploymentContext.getDeploymentPaaSId();
    //final String a4cDeploymentId = deploymentContext.getDeployment().getId();
    String orchestratorUuidDeployment = null;

    User user = userService.retrieveUser(deploymentContext.getDeployment().getDeployerUsername());
    PluginDeploymentStatus<DeploymentStatus> dds = 
        topologyDeploymentStatusManager.getStatusByA4cDeploymentPaasId(a4cDeploymentPaasId);
    
    try {
      
      if (dds != null) {
        orchestratorUuidDeployment = dds.getOrchestratorDeploymentUuid();
        if (orchestratorUuidDeployment != null) {
          log.info(String.format("Undeploying on orchestrator %s paas %s",
              deploymentContext.getDeployment().getOrchestratorId(),
              a4cDeploymentPaasId));
          final OrchestratorResponse result = orchestratorConnector.callUndeploy(configuration,
              user.getUsername(),
              user.getPlainPassword(), orchestratorUuidDeployment);
          
          topologyDeploymentStatusManager.updateStatusByA4cDeploymentPaasId(
              a4cDeploymentPaasId, null, DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS);
          QueryBuilder qb = QueryBuilders.termQuery("a4cDeploymentId", dds.getA4cDeploymentId());
          long count = alienMonitorDao.count(DeployedTopologyInformation.class, qb);
          log.info("Count before deletion: " + count);
          alienMonitorDao.delete(DeployedTopologyInformation.class, qb);
          alienMonitorDao.count(DeployedTopologyInformation.class, qb);
          count = alienMonitorDao.count(DeployedTopologyInformation.class, qb);
          log.info("Count after deletion: " + count);
        }
      }
      callback.onSuccess(null);
    } catch (IOException | NoSuchFieldException | OrchestratorIamException er) {
      log.error("Error undeployment", er);
      callback.onFailure(er);
      topologyDeploymentStatusManager.updateStatusByA4cDeploymentPaasId(
          a4cDeploymentPaasId, er, DeploymentStatus.FAILURE);
    }
  }

  @Override
  public void getEventsSince(Date date, int maxEvents,
      IPaaSCallback<AbstractMonitorEvent[]> eventCallback) {
    //eventCallback.onSuccess(eventService.flushEvents(date, maxEvents));
    // log.info("call getEventsSince");

  }

  @Override
  public void getInstancesInformation(PaaSTopologyDeploymentContext deploymentContext,
      IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
    log.info("call getInstancesInformation");
    statusService.getInstancesInformation(deploymentContext, callback);
    
  }

  @Override
  public void getStatus(PaaSDeploymentContext deploymentContext,
      IPaaSCallback<DeploymentStatus> callback) {
    log.info("call getStatus");
    statusService.getStatus(deploymentContext, callback);
  }

  @Override
  public void update(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
    return;
  }

  @Override
  public List<PluginArchive> pluginArchives() {
    return Collections.emptyList();
  }

  /** ****** Not implemented. */
  @Override
  public void scale(PaaSDeploymentContext deploymentContext, String nodeTemplateId, int instances,
      IPaaSCallback<?> callback) {}

  @Override
  public void launchWorkflow(PaaSDeploymentContext deploymentContext, String workflowName,
      Map<String, Object> inputs, IPaaSCallback<?> callback) {
    throw new NotImplementedException();
  }

  @Override
  public void executeOperation(PaaSTopologyDeploymentContext deploymentContext,
      NodeOperationExecRequest request, IPaaSCallback<Map<String, String>> operationResultCallback)
      throws OperationExecutionException {}

  @Override
  public void switchInstanceMaintenanceMode(PaaSDeploymentContext deploymentContext, String nodeId,
      String instanceId, boolean maintenanceModeOn) throws MaintenanceModeException {}

  @Override
  public void switchMaintenanceMode(PaaSDeploymentContext deploymentContext,
      boolean maintenanceModeOn) throws MaintenanceModeException {}
}
