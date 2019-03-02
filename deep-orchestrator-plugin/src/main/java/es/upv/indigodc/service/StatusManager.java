package es.upv.indigodc.service;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.dao.model.GetMultipleDataResult;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import es.upv.indigodc.service.model.PluginDeploymentStatus;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;

@Service
@Slf4j
public class StatusManager<TYPE_STATUS extends  Serializable> {

  protected ConcurrentMap<String, PluginDeploymentStatus<TYPE_STATUS>> statusByA4cDeploymentPaasId;
  protected ConcurrentMap<String, PluginDeploymentStatus<TYPE_STATUS>> statusByA4cDeploymentId;
  protected ConcurrentMap<String, PluginDeploymentStatus<TYPE_STATUS>> statusByOrchestratorDeploymentUuid;
  
  @PostConstruct
  public void init() {
    statusByA4cDeploymentPaasId = new ConcurrentHashMap<>();
    statusByA4cDeploymentId = new ConcurrentHashMap<>();
    statusByOrchestratorDeploymentUuid = new ConcurrentHashMap<>();

//    // Try to retrieve the last deployment status event to initialize the cache
//    Map<String, String[]> filters = Maps.newHashMap();
//    //filters.put("deploymentId", new String[] { contextEntry.getValue().getDeploymentId() });
//    GetMultipleDataResult<PluginDeploymentStatus<TYPE_STATUS>> lastEventResult = alienMonitorDao
//        .<TYPE_STATUS>search(PluginDeploymentStatus.class, null, 
//            filters, null, null, 0, 1, "date", true);
  }
  
  public synchronized PluginDeploymentStatus<TYPE_STATUS> getStatusByA4cDeploymentPaasId(String a4cDeploymentPaasId) {
    PluginDeploymentStatus<TYPE_STATUS> status = statusByA4cDeploymentPaasId.get(a4cDeploymentPaasId);
    if (status != null) 
      return new PluginDeploymentStatus<>(status);
    else
      return null;
  }
  
  public synchronized void updateStatusByA4cDeploymentPaasId(String a4cDeploymentPaasId,
      Throwable error, TYPE_STATUS deploymentStatus) {
    PluginDeploymentStatus<TYPE_STATUS> dds = statusByA4cDeploymentPaasId.get(a4cDeploymentPaasId);
    if (dds != null) {
      this.removeStatusByA4cDeploymentPaasId(a4cDeploymentPaasId);
      addDeepDeploymentStatus(dds.getA4cDeploymentPaasId(), dds.getA4cDeploymentId(),
          dds.getOrchestratorDeploymentUuid(), dds.getOrchestratorId(),
          dds.getDeployerUsername(), error, deploymentStatus);
    }
  }
  
  public synchronized void removeStatusByA4cDeploymentPaasId(String a4cDeploymentPaasId) {
    PluginDeploymentStatus<TYPE_STATUS> dds = statusByA4cDeploymentPaasId.get(a4cDeploymentPaasId);
    if (dds != null) {
      statusByA4cDeploymentPaasId.remove(a4cDeploymentPaasId);
      statusByA4cDeploymentId.remove(dds.getA4cDeploymentId());
      statusByOrchestratorDeploymentUuid.remove(dds.getOrchestratorDeploymentUuid());      
    }
  }
  
  public synchronized PluginDeploymentStatus<TYPE_STATUS> getStatusByA4cDeploymentId(String a4cDeploymentId) {
    PluginDeploymentStatus<TYPE_STATUS> status = statusByA4cDeploymentId.get(a4cDeploymentId);
    if (status != null)
      return new PluginDeploymentStatus<>(status);
    else
      return null;
  }
  
  public synchronized void updateStatusByA4cDeploymentId(String a4cDeploymentId,
      Throwable error,  TYPE_STATUS deploymentStatus) {
    PluginDeploymentStatus<TYPE_STATUS> dds = statusByA4cDeploymentId.get(a4cDeploymentId);
    if (dds != null) {
      this.removeStatusByA4cDeploymentId(a4cDeploymentId);
      addDeepDeploymentStatus(dds.getA4cDeploymentPaasId(), dds.getA4cDeploymentId(),
          dds.getOrchestratorDeploymentUuid(), dds.getOrchestratorId(),
          dds.getDeployerUsername(), error, deploymentStatus);
      
    }
  }
  
  public synchronized void removeStatusByA4cDeploymentId(String a4cDeploymentId) {
    PluginDeploymentStatus<TYPE_STATUS> dds = statusByA4cDeploymentId.get(a4cDeploymentId);
    if (dds != null) {
      statusByA4cDeploymentPaasId.remove(dds.getA4cDeploymentPaasId());
      statusByA4cDeploymentId.remove(a4cDeploymentId);
      statusByOrchestratorDeploymentUuid.remove(dds.getOrchestratorDeploymentUuid());      
    }
  }
  
  public synchronized PluginDeploymentStatus<TYPE_STATUS> getStatusByOrchestratorDeploymentUuid(String orchestratorDeploymentUuid) {
    return statusByOrchestratorDeploymentUuid.get(orchestratorDeploymentUuid);
  }
  
  public synchronized void updateStatusByOrchestratorDeploymentUuid(String orchestratorDeploymentUuid,
      Throwable error, TYPE_STATUS deploymentStatus) {
    PluginDeploymentStatus<TYPE_STATUS> dds = statusByOrchestratorDeploymentUuid.get(orchestratorDeploymentUuid);
    if (dds != null) {
      this.removeStatusByOrchestratorDeploymentUuid(orchestratorDeploymentUuid);
      addDeepDeploymentStatus(dds.getA4cDeploymentPaasId(), dds.getA4cDeploymentId(),
          dds.getOrchestratorDeploymentUuid(), dds.getOrchestratorId(),
          dds.getDeployerUsername(), error, deploymentStatus);
      
    }
  }
  
  public synchronized void removeStatusByOrchestratorDeploymentUuid(String orchestratorDeploymentUuid) {
    PluginDeploymentStatus<TYPE_STATUS> dds = statusByOrchestratorDeploymentUuid.get(orchestratorDeploymentUuid);
    if (dds != null) {
      statusByA4cDeploymentPaasId.remove(dds.getA4cDeploymentPaasId());
      statusByA4cDeploymentId.remove(dds.getA4cDeploymentId());
      statusByOrchestratorDeploymentUuid.remove(orchestratorDeploymentUuid);      
    }
  }
  
  public synchronized void addDeepDeploymentStatus(String a4cDeploymentPaasId, String a4cDeploymentId,
      String orchestratorDeploymentUuid, String orchestratorId, String deployerUsername, Throwable error, TYPE_STATUS status) {
    PluginDeploymentStatus<TYPE_STATUS> dds = new PluginDeploymentStatus<TYPE_STATUS>(a4cDeploymentPaasId, a4cDeploymentId,
        orchestratorDeploymentUuid, orchestratorId, deployerUsername, error, status);
    statusByA4cDeploymentPaasId.put(a4cDeploymentPaasId, dds);
    
    // It is possible that we only have the a4cDeploymentPaasId (for instance
    // when a deployment failed)
    if (a4cDeploymentId != null)
      statusByA4cDeploymentId.put(a4cDeploymentId, dds);
    if (orchestratorDeploymentUuid != null)
      statusByOrchestratorDeploymentUuid.put(orchestratorDeploymentUuid, dds);
    
  }
  
  public synchronized final Collection<PluginDeploymentStatus<TYPE_STATUS>> getActiveDeployments() {
    return statusByA4cDeploymentPaasId.values().stream()
        .filter(dds -> dds.getStatus() != DeploymentStatus.UNDEPLOYED 
          && dds.getStatus() != DeploymentStatus.FAILURE)
        .map(status -> new PluginDeploymentStatus<>(status))
        .collect(Collectors.toList());
  }
  
}
