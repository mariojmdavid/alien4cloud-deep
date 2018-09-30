package es.upv.indigodc.service;

import javax.annotation.Resource;
import java.io.IOException;
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
  protected EventService eventService;
  
  @Resource
  protected UserService userService;
  
  public static class ObtainStatusDeployment implements Runnable {
    
    @Autowired
    protected OrchestratorConnector orchestratorConnector;
    
    protected EventService eventService;
    
    public ObtainStatusDeployment(EventService eventService) {
      this.eventService = eventService;
    }

    @Override
    public void run() {
      
    }
      
    
  }
  
  public void init(Map<String, PaaSTopologyDeploymentContext> activeDeploymentContexts) {
    threadpool = Executors.newScheduledThreadPool(1);
    for (Map.Entry<String, PaaSTopologyDeploymentContext> contextEntry : activeDeploymentContexts.entrySet()) {
        String a4cDeploymentPaaSId = contextEntry.getKey();
        // Try to retrieve the last deployment status event to initialize the cache
        Map<String, String[]> filters = Maps.newHashMap();
        filters.put("deploymentId", new String[] { contextEntry.getValue().getDeploymentId() });
        GetMultipleDataResult<PaaSDeploymentStatusMonitorEvent> lastEventResult = 
            alienMonitorDao.search(PaaSDeploymentStatusMonitorEvent.class, null,
                filters, null, null, 0, 1, "date", true);
//        if (lastEventResult.getData() != null && lastEventResult.getData().length > 0) {
//            statusCache.put(deploymentPaaSId, lastEventResult.getData()[0].getDeploymentStatus());
//        }
        // Query the manager to be sure that the status has not changed
        getStatusFromDeep(a4cDeploymentPaaSId);
    }
    ObtainStatusDeployment osd = new ObtainStatusDeployment(eventService);
    statusHandle = threadpool.scheduleAtFixedRate(osd, 10, 10, TimeUnit.SECONDS);
  }
  
  public void destroy() {
    threadpool.shutdownNow();
  }
  
  public void getStatus(PaaSDeploymentContext deploymentContext,
      IPaaSCallback<DeploymentStatus> callback) {
    log.info("call get status");
    String a4cUuidDeployment = deploymentContext.getDeployment().getId();

    final OrchestratorDeploymentMapping orchestratorDeploymentMapping =
        eventService..getByAlienDeploymentId(a4cUuidDeployment);
    if (orchestratorDeploymentMapping != null) {
      final String orchestratorUuidDeployment =
          orchestratorDeploymentMapping.getOrchestratorUuidDeployment();
      if (orchestratorUuidDeployment != null) {
        final CloudConfiguration configuration = cloudConfigurationManager
            .getCloudConfiguration(deploymentContext.getDeployment().getOrchestratorId());
        try {
          OrchestratorResponse response = orchestratorConnector.callDeploymentStatus(configuration,
              userService.getCurrentUser().getUsername(),
              userService.getCurrentUser().getPlainPassword(), orchestratorUuidDeployment);
          String statusTopologyDeployment = response.getStatusTopologyDeployment();
          callback.onSuccess(
              Util.indigoDcStatusToDeploymentStatus(statusTopologyDeployment.toUpperCase()));

        } catch (NoSuchFieldException er) {
          log.error("Error getStatus", er);
          callback.onFailure(er);
          callback.onSuccess(DeploymentStatus.UNKNOWN);
        } catch (IOException er) {
          log.error("Error getStatus", er);
          callback.onFailure(er);
          callback.onSuccess(DeploymentStatus.UNKNOWN);
        } catch (OrchestratorIamException er) {
          switch (er.getHttpCode()) {
            case 404:
              callback.onSuccess(DeploymentStatus.UNDEPLOYED);
              break;
            default:
              callback.onFailure(er);
          }
          log.error("Error deployment ", er);
        } catch (StatusNotFoundException er) {
          callback.onFailure(er);
          er.printStackTrace();
        }
      } else {
        callback.onSuccess(DeploymentStatus.UNDEPLOYED);
      }
    } else {
      callback.onSuccess(DeploymentStatus.UNDEPLOYED);
    }
  }
  
  /**
   * Get the status from the orchestrator by querying the manager. 
   * If the deployment not found in the cache then will begin to monitor it.
   * 
   * @param deploymentPaaSId the deployment id
   * @return status retrieved from cloudify
   */
  public DeepDeploymentStatus getStatusFromDeep(String a4cDeploymentPaaSId) {
    DeepDeploymentStatus deploymentStatus;
      try {
          deploymentStatus = asyncGetStatus(a4cDeploymentPaaSId).get();
      } catch (Exception e) {
          log.error("Failed to get status of application " + deploymentPaaSId, e);
          deploymentStatus = DeploymentStatus.UNKNOWN;
      }
      registerDeploymentStatusAndReschedule(deploymentPaaSId, deploymentStatus);
      return deploymentStatus;
  }
  
  private void registerDeploymentStatusAndReschedule(String a4cDeploymentPaasId, String a4cDeploymentId, 
      String orchestratorDeploymentUuid, String orchestratorId, DeploymentStatus status) {
      registerDeploymentStatus(a4cDeploymentPaasId, a4cDeploymentId, 
          orchestratorDeploymentUuid, orchestratorId, status);
      if (!DeploymentStatus.UNDEPLOYED.equals(status)) {
          scheduleRefreshStatus(a4cDeploymentPaasId, a4cDeploymentId, 
              orchestratorDeploymentUuid, orchestratorId, status);
      }
  }
  
  private ListenableFuture<DeploymentStatus> asyncGetStatus(String a4cDeploymentPaasId) {
    ListenableFuture<Deployment> deploymentFuture = cloudConfigurationManager.getApiClient().getDeploymentClient().asyncRead(deploymentPaaSId);
    AsyncFunction<Deployment, Execution[]> executionsAdapter = deployment -> cloudConfigurationManager.getApiClient().getExecutionClient()
            .asyncList(deployment.getId(), false);
    ListenableFuture<Execution[]> executionsFuture = Futures.transform(deploymentFuture, executionsAdapter);
    Function<Execution[], DeploymentStatus> deploymentStatusAdapter = this::doGetStatus;
    ListenableFuture<DeploymentStatus> statusFuture = Futures.transform(executionsFuture, deploymentStatusAdapter);
    return Futures.withFallback(statusFuture, throwable -> {
        // In case of error we give back unknown status and let the next polling determine the application status
        if (throwable instanceof CloudifyAPIException) {
            if (Objects.equals(HttpStatus.NOT_FOUND, ((CloudifyAPIException) throwable).getStatusCode())) {
                // Only return undeployed for an application if we received a 404 which means it was deleted
                log.info("Application " + deploymentPaaSId + " is not found on cloudify");
                return Futures.immediateFuture(DeploymentStatus.UNDEPLOYED);
            }
        }
        log.warn("Unable to retrieve status for application " + deploymentPaaSId + ", its status will pass to " + DeploymentStatus.UNKNOWN, throwable);
        return Futures.immediateFuture(DeploymentStatus.UNKNOWN);
    });
}
  
    private Map<String, DeploymentStatus> statusCache = Maps.newHashMap();

    private Map<String, ListenableScheduledFuture<?>> statusRefreshJobs = Maps.newHashMap();

    private ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();

    @Resource
    private IndigoDcOrchestratorFactory indigoDcOrchestratorFactory;



    
    @Resource
    private OrchestratorConnector orchestratorConnector;

    //@Resource
    //private RuntimePropertiesService runtimePropertiesService;

    @Resource(name = "cloudify-async-thread-pool")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Resource
    private ListeningScheduledExecutorService scheduler;

    public void scheduleRefreshStatus(final String deploymentPaaSId) {
        scheduleRefreshStatus(deploymentPaaSId, statusCache.get(deploymentPaaSId));
    }

    private void scheduleRefreshStatus(String a4cDeploymentPaasId, String a4cDeploymentId, 
        String orchestratorDeploymentUuid, String orchestratorId, DeploymentStatus status) {
        long scheduleTime;
        switch (status) {
        case DEPLOYMENT_IN_PROGRESS:
        case UNDEPLOYMENT_IN_PROGRESS:
            // Poll more aggressively if deployment in progress or undeployment in progress
            scheduleTime = indigoDcOrchestratorFactory.getDefaultConfiguration().getOrchestratorPollInterval();
            break;
        default:
            scheduleTime = indigoDcOrchestratorFactory.getDefaultConfiguration().getOrchestratorPollInterval();
            break;
        }
        Runnable job = () -> {
            if (log.isDebugEnabled()) {
                log.debug("Running refresh state for {} with current state {}", a4cDeploymentPaasId, status);
            }
            try {
                cacheLock.readLock().lock();
                // It means someone cleaned entry before the scheduled task run
                if (!statusCache.containsKey(a4cDeploymentPaasId)) {
                    return;
                }
            } finally {
                cacheLock.readLock().unlock();
            }
            ListenableFuture<DeploymentStatus> newStatusFuture = asyncGetStatus(a4cDeploymentPaasId);
            Function<DeploymentStatus, DeploymentStatus> newStatusAdapter = newStatus -> {
                registerDeploymentStatusAndReschedule(a4cDeploymentPaasId, newStatus);
                return newStatus;
            };
            ListenableFuture<DeploymentStatus> refreshFuture = Futures.transform(newStatusFuture, newStatusAdapter::apply);
            Futures.addCallback(refreshFuture, new FutureCallback<DeploymentStatus>() {
                @Override
                public void onSuccess(DeploymentStatus result) {
                    if (log.isDebugEnabled()) {
                        log.debug("Successfully refreshed state for {} with new state {}", a4cDeploymentPaasId, result);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    log.warn("Failed to refresh state for " + a4cDeploymentPaasId, t);
                }
            });
        };
        try {
            cacheLock.writeLock().lock();
            if (!isApplicationMonitored(a4cDeploymentPaasId)) {
                // Don't relaunch a schedule if one has been configured
                statusRefreshJobs.put(a4cDeploymentPaasId, scheduler.schedule(job, scheduleTime, TimeUnit.SECONDS));
            } else {
                log.info("Application " + a4cDeploymentPaasId + " has already been monitored, ignore scheduling request");
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }



    private ListenableFuture<DeploymentStatus> asyncGetStatus(String deploymentPaaSId) {
        ListenableFuture<Deployment> deploymentFuture = cloudConfigurationManager.getApiClient().getDeploymentClient().asyncRead(deploymentPaaSId);
        AsyncFunction<Deployment, Execution[]> executionsAdapter = deployment -> cloudConfigurationManager.getApiClient().getExecutionClient()
                .asyncList(deployment.getId(), false);
        ListenableFuture<Execution[]> executionsFuture = Futures.transform(deploymentFuture, executionsAdapter);
        Function<Execution[], DeploymentStatus> deploymentStatusAdapter = this::doGetStatus;
        ListenableFuture<DeploymentStatus> statusFuture = Futures.transform(executionsFuture, deploymentStatusAdapter);
        return Futures.withFallback(statusFuture, throwable -> {
            // In case of error we give back unknown status and let the next polling determine the application status
            if (throwable instanceof CloudifyAPIException) {
                if (Objects.equals(HttpStatus.NOT_FOUND, ((CloudifyAPIException) throwable).getStatusCode())) {
                    // Only return undeployed for an application if we received a 404 which means it was deleted
                    log.info("Application " + deploymentPaaSId + " is not found on cloudify");
                    return Futures.immediateFuture(DeploymentStatus.UNDEPLOYED);
                }
            }
            log.warn("Unable to retrieve status for application " + deploymentPaaSId + ", its status will pass to " + DeploymentStatus.UNKNOWN, throwable);
            return Futures.immediateFuture(DeploymentStatus.UNKNOWN);
        });
    }

    private DeploymentStatus doGetStatus(Execution[] executions) {
        Execution lastExecution = null;
        // Get the last install or uninstall execution, to check for status
        for (Execution execution : executions) {
            if (log.isDebugEnabled()) {
                log.debug("Deployment {} has execution {} created at {} for workflow {} in status {}", 
                    execution.getDeploymentId(), execution.getId(),
                        execution.getCreatedAt(), execution.getWorkflowId(), execution.getStatus());
            }
            Set<String> relevantExecutionsForStatus = Sets.newHashSet(Workflow.INSTALL, Workflow.DELETE_DEPLOYMENT_ENVIRONMENT,
                    Workflow.CREATE_DEPLOYMENT_ENVIRONMENT, Workflow.UNINSTALL, Workflow.UPDATE_DEPLOYMENT, Workflow.POST_UPDATE_DEPLOYMENT);
            // Only consider install/uninstall workflow to check for deployment status
            if (relevantExecutionsForStatus.contains(execution.getWorkflowId())) {
                if (lastExecution == null) {
                    lastExecution = execution;
                } else if (DateUtil.compare(execution.getCreatedAt(), lastExecution.getCreatedAt()) > 0) {
                    lastExecution = execution;
                }
            }
        }
        if (lastExecution == null) {
            // No install and uninstall yet it must be deployment in progress
            return DeploymentStatus.DEPLOYMENT_IN_PROGRESS;
        } else {
            if (ExecutionStatus.isCancelled(lastExecution.getStatus())) {
                // The only moment when we cancel a running execution is when we undeploy
                return DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS;
            }
            // Only consider changing state when an execution has been finished or in failure
            // Execution in cancel or starting will return null to not impact on the application state as they are intermediary state
            switch (lastExecution.getWorkflowId()) {
            case Workflow.CREATE_DEPLOYMENT_ENVIRONMENT:
                if (ExecutionStatus.isInProgress(lastExecution.getStatus()) || ExecutionStatus.isTerminatedSuccessfully(lastExecution.getStatus())) {
                    return DeploymentStatus.INIT_DEPLOYMENT;
                } else if (ExecutionStatus.isTerminatedWithFailure(lastExecution.getStatus())) {
                    return DeploymentStatus.FAILURE;
                } else {
                    return DeploymentStatus.UNKNOWN;
                }
            case Workflow.INSTALL:
                if (ExecutionStatus.isInProgress(lastExecution.getStatus())) {
                    return DeploymentStatus.DEPLOYMENT_IN_PROGRESS;
                } else if (ExecutionStatus.isTerminatedSuccessfully(lastExecution.getStatus())) {
                    return DeploymentStatus.DEPLOYED;
                } else if (ExecutionStatus.isTerminatedWithFailure(lastExecution.getStatus())) {
                    return DeploymentStatus.FAILURE;
                } else {
                    return DeploymentStatus.UNKNOWN;
                }
            case Workflow.UNINSTALL:
                if (ExecutionStatus.isInProgress(lastExecution.getStatus()) || ExecutionStatus.isTerminatedSuccessfully(lastExecution.getStatus())) {
                    return DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS;
                } else if (ExecutionStatus.isTerminatedWithFailure(lastExecution.getStatus())) {
                    return DeploymentStatus.FAILURE;
                } else {
                    return DeploymentStatus.UNKNOWN;
                }
            case Workflow.DELETE_DEPLOYMENT_ENVIRONMENT:
                if (ExecutionStatus.isInProgress(lastExecution.getStatus())) {
                    return DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS;
                } else if (ExecutionStatus.isTerminatedSuccessfully(lastExecution.getStatus())) {
                    return DeploymentStatus.UNDEPLOYED;
                } else if (ExecutionStatus.isTerminatedWithFailure(lastExecution.getStatus())) {
                    return DeploymentStatus.FAILURE;
                } else {
                    return DeploymentStatus.UNKNOWN;
                }
            case Workflow.UPDATE_DEPLOYMENT:
            case Workflow.POST_UPDATE_DEPLOYMENT:
                if (ExecutionStatus.isInProgress(lastExecution.getStatus())) {
                    return DeploymentStatus.UPDATE_IN_PROGRESS;
                } else if (ExecutionStatus.isTerminatedSuccessfully(lastExecution.getStatus())) {
                    return DeploymentStatus.UPDATED;
                } else if (ExecutionStatus.isTerminatedWithFailure(lastExecution.getStatus())) {
                    return DeploymentStatus.UPDATE_FAILURE;
                } else {
                    return DeploymentStatus.UNKNOWN;
                }
            default:
                return DeploymentStatus.UNKNOWN;
            }
        }
    }

    /**
     * Get status from local in memory cache
     * 
     * @param deploymentPaaSId the deployment id
     * @return status of the deployment uniquely from the in memory cache
     */
    private DeploymentStatus getStatusFromCache(String deploymentPaaSId) {
        try {
            cacheLock.readLock().lock();
            if (!statusCache.containsKey(deploymentPaaSId)) {
                return DeploymentStatus.UNDEPLOYED;
            } else {
                return statusCache.get(deploymentPaaSId);
            }
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Get fresh status, only INIT_DEPLOYMENT will be sent back from cache, else retrieve the status from cloudify.
     * This is called to secure deploy and undeploy, to be sure that we have the most fresh status and not the one from cache
     * 
     * @param deploymentPaaSId id of the deployment to check
     * @return the deployment status
     */
    public DeploymentStatus getFreshStatus(String deploymentPaaSId) {
        try {
            cacheLock.readLock().lock();
            DeploymentStatus statusFromCache = getStatusFromCache(deploymentPaaSId);
            if (DeploymentStatus.INIT_DEPLOYMENT == statusFromCache) {
                // The deployment is being created
                return statusFromCache;
            }
        } finally {
            cacheLock.readLock().unlock();
        }
        // This will refresh the status of the application from cloudify
        return getStatusFromDeep(deploymentPaaSId);
    }



    /**
     * This is used to retrieve the status of the application, get from cache for monitored entries else get from DEEP
     * 
     * @param deploymentPaaSId the deployment id
     * @param callback the callback when the status is ready
     */
    public void getStatus(String deploymentPaaSId, IPaaSCallback<DeploymentStatus> callback) {
        try {
            cacheLock.readLock().lock();
            DeploymentStatus statusFromCache = getStatusFromCache(deploymentPaaSId);
            if (DeploymentStatus.INIT_DEPLOYMENT == statusFromCache || isApplicationMonitored(deploymentPaaSId)) {
                // The deployment is being created means that currently it's not monitored, it's in transition
                // The deployment is currently monitored so the cache can be used
                threadPoolTaskExecutor.execute(() -> callback.onSuccess(statusFromCache));
            } else {
                Futures.addCallback(asyncGetStatus(deploymentPaaSId), new FutureCallback<DeploymentStatus>() {
                    @Override
                    public void onSuccess(DeploymentStatus newDeploymentStatus) {
                        try {
                            cacheLock.readLock().lock();
                            DeploymentStatus statusFromCache = getStatusFromCache(deploymentPaaSId);
                            if (DeploymentStatus.INIT_DEPLOYMENT == statusFromCache || isApplicationMonitored(deploymentPaaSId)) {
                                callback.onSuccess(statusFromCache);
                                // It's from cache nothing else to do
                                return;
                            } else {
                                callback.onSuccess(newDeploymentStatus);
                                // Will continue to registerDeploymentStatusAndReschedule
                            }
                        } finally {
                            cacheLock.readLock().unlock();
                        }
                        try {
                            cacheLock.writeLock().lock();
                            // A deployment has kicked in in concurrence, tricky situation
                            if (DeploymentStatus.INIT_DEPLOYMENT != getStatusFromCache(deploymentPaaSId)) {
                                doRegisterDeploymentStatus(a4cDeploymentPaasId, a4cDeploymentId, 
                                    orchestratorDeploymentUuid, orchestratorId, newDeploymentStatus);
                                // Do not schedule anything then
                                return;
                            }
                        } finally {
                            cacheLock.writeLock().unlock();
                        }
                        if (!DeploymentStatus.UNDEPLOYED.equals(newDeploymentStatus)) {
                            scheduleRefreshStatus(a4cDeploymentPaasId, a4cDeploymentId, 
                                orchestratorDeploymentUuid, orchestratorId, newDeploymentStatus);
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        callback.onFailure(t);
                    }
                });
            }
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    public void getInstancesInformation(final PaaSTopologyDeploymentContext deploymentContext,
            final IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
        try {
            cacheLock.readLock().lock();
            if (!statusCache.containsKey(deploymentContext.getDeploymentPaaSId())) {
                callback.onSuccess(Maps.<String, Map<String, InstanceInformation>> newHashMap());
                return;
            }
        } finally {
            cacheLock.readLock().unlock();
        }

        ListenableFuture<NodeInstance[]> instancesFuture = cloudConfigurationManager.getApiClient().getNodeInstanceClient()
                .asyncList(deploymentContext.getDeploymentPaaSId());
        ListenableFuture<Node[]> nodesFuture = cloudConfigurationManager.getApiClient().getNodeClient().asyncList(deploymentContext.getDeploymentPaaSId(), null);
        ListenableFuture<List<AbstractCloudifyModel[]>> combinedFutures = Futures.allAsList(instancesFuture, nodesFuture);
        Futures.addCallback(combinedFutures, new FutureCallback<List<AbstractCloudifyModel[]>>() {
            @Override
            public void onSuccess(List<AbstractCloudifyModel[]> nodeAndNodeInstances) {
                NodeInstance[] instances = (NodeInstance[]) nodeAndNodeInstances.get(0);
                Node[] nodes = (Node[]) nodeAndNodeInstances.get(1);
                Map<String, Node> nodeMap = Maps.newHashMap();
                for (Node node : nodes) {
                    nodeMap.put(node.getId(), node);
                }

                Map<String, NodeInstance> nodeInstanceMap = Maps.newHashMap();
                for (NodeInstance instance : instances) {
                    nodeInstanceMap.put(instance.getId(), instance);
                }

                Map<String, Map<String, InstanceInformation>> information = Maps.newHashMap();
                for (NodeInstance instance : instances) {
                    NodeTemplate nodeTemplate = deploymentContext.getDeploymentTopology().getNodeTemplates().get(instance.getNodeId());
                    if (nodeTemplate == null) {
                        // Sometimes we have generated instance that do not exist in alien topology
                        continue;
                    }
                    Map<String, InstanceInformation> nodeInformation = information.get(instance.getNodeId());
                    if (nodeInformation == null) {
                        nodeInformation = Maps.newHashMap();
                        information.put(instance.getNodeId(), nodeInformation);
                    }
                    String instanceId = instance.getId();
                    InstanceInformation instanceInformation = new InstanceInformation();
                    instanceInformation.setState(instance.getState());
                    InstanceStatus instanceStatus = NodeInstanceStatus.getInstanceStatusFromState(instance.getState());
                    if (instanceStatus == null) {
                        continue;
                    } else {
                        instanceInformation.setInstanceStatus(instanceStatus);
                    }
                    Map<String, String> runtimeProperties = null;
                    try {
                        runtimeProperties = MapUtil.toString(instance.getRuntimeProperties());
                    } catch (JsonProcessingException e) {
                        log.error("Unable to stringify runtime properties", e);
                    }
                    instanceInformation.setRuntimeProperties(runtimeProperties);

                    nodeInformation.put(instanceId, instanceInformation);
                }
                String floatingIpPrefix = eventService.getMappingConfiguration().getGeneratedNodePrefix() + "_floating_ip_";
                for (NodeInstance instance : instances) {
                    if (instance.getId().startsWith(floatingIpPrefix)) {
                        // It's a floating ip then must fill the compute with public ip address
                        String computeNodeId = instance.getNodeId().substring(floatingIpPrefix.length());
                        Map<String, InstanceInformation> computeNodeInformation = information.get(computeNodeId);
                        if (MapUtils.isNotEmpty(computeNodeInformation)) {
                            InstanceInformation firstComputeInstanceFound = computeNodeInformation.values().iterator().next();
                            firstComputeInstanceFound.getAttributes().put("public_ip_address",
                                    String.valueOf(instance.getRuntimeProperties().get("floating_ip_address")));
                        }
                    }
                }
                callback.onSuccess(information);
            }

            @Override
            public void onFailure(Throwable t) {
                if (log.isDebugEnabled()) {
                    log.debug("Problem retrieving instance information for deployment <" + deploymentContext.getDeploymentPaaSId() + "> ");
                }
                callback.onSuccess(Maps.newHashMap());
            }
        });
    }

    /**
     * Register for the first time a deployment with a status to the cache
     * 
     * @param deploymentPaaSId the deployment id
     */
    public void registerDeployment(String a4cDeploymentPaasId, String a4cDeploymentId, 
        String orchestratorDeploymentUuid, String orchestratorId) {
        registerDeploymentStatus(a4cDeploymentPaasId, a4cDeploymentId, 
            orchestratorDeploymentUuid, orchestratorId, DeploymentStatus.INIT_DEPLOYMENT);
    }

    /**
     * Register a new deployment status of an existing deployment
     *
     * @param deploymentPaaSId the deployment id
     * @param newDeploymentStatus the new deployment status
     */
    public void registerDeploymentStatus(String a4cDeploymentPaasId, String a4cDeploymentId, 
        String orchestratorDeploymentUuid, String orchestratorId, DeploymentStatus status) {
        try {
            cacheLock.writeLock().lock();
            doRegisterDeploymentStatus(a4cDeploymentPaasId, a4cDeploymentId, 
                orchestratorDeploymentUuid, orchestratorId, status);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    private void doRegisterDeploymentStatus(String a4cDeploymentPaasId, String a4cDeploymentId, 
        String orchestratorDeploymentUuid, String orchestratorId, DeploymentStatus status) {
        DeploymentStatus deploymentStatus = getStatusFromCache(a4cDeploymentPaasId);
        if (!status.equals(deploymentStatus)) {
            // Only register event if it makes changes to the cache
            if (DeploymentStatus.UNDEPLOYED.equals(status)) {
                if (DeploymentStatus.INIT_DEPLOYMENT != deploymentStatus) {
                    // Application has been removed, don't need to monitor it anymore
                    statusCache.remove(a4cDeploymentPaasId);
                    statusRefreshJobs.remove(a4cDeploymentPaasId);
                    log.info("Application [" + a4cDeploymentPaasId + "] has been undeployed");
                } else {
                    // a deployment in INIT_DEPLOYMENT must come to state DEPLOYMENT_IN_PROGRESS
                    log.info("Concurrent access to deployment [" + a4cDeploymentPaasId + "], ignore transition from INIT_DEPLOYMENT to UNDEPLOYED");
                }
            } else {
                log.info("Application [" + a4cDeploymentPaasId + "] passed from status " + deploymentStatus + " to " + status);
                // Deployment status has changed
                statusCache.put(a4cDeploymentPaasId, status);
            }
            // Send back event to Alien only if it's a known status
            eventService.registerDeploymentEvent(a4cDeploymentPaasId, 
                a4cDeploymentId, orchestratorDeploymentUuid, orchestratorId, status);
        }
    }

    private boolean isApplicationMonitored(String deploymentPaaSId) {
        return statusRefreshJobs.containsKey(deploymentPaaSId) && !statusRefreshJobs.get(deploymentPaaSId).isDone();
    }


}
