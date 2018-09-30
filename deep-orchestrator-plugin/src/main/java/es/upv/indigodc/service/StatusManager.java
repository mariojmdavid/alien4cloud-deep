package es.upv.indigodc.service;

import alien4cloud.paas.model.DeploymentStatus;
import es.upv.indigodc.service.model.DeepDeploymentStatus;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class StatusManager {

  protected ConcurrentMap<String, DeepDeploymentStatus> statusByA4cDeploymentPaasId;
  protected ConcurrentMap<String, DeepDeploymentStatus> statusByA4cDeploymentId;
  protected ConcurrentMap<String, DeepDeploymentStatus> statusByOrchestratorDeploymentUuid;
  
  public void init() {
    statusByA4cDeploymentPaasId = new ConcurrentHashMap<>();
    statusByA4cDeploymentId = new ConcurrentHashMap<>();
    statusByOrchestratorDeploymentUuid = new ConcurrentHashMap<>();
  }
  
  public synchronized DeepDeploymentStatus getStatusByA4cDeploymentPaasId(String a4cDeploymentPaasId) {
    return statusByA4cDeploymentPaasId.get(a4cDeploymentPaasId);
  }
  
  public synchronized void updateStatusByA4cDeploymentPaasId(String a4cDeploymentPaasId,
      Throwable error, DeploymentStatus deploymentStatus) {
    DeepDeploymentStatus dds = statusByA4cDeploymentPaasId.get(a4cDeploymentPaasId);
    if (dds != null)
      dds.setStatus(deploymentStatus);
  }
  
  public synchronized void removeStatusByA4cDeploymentPaasId(String a4cDeploymentPaasId) {
    DeepDeploymentStatus dds = statusByA4cDeploymentPaasId.get(a4cDeploymentPaasId);
    if (dds != null) {
      statusByA4cDeploymentPaasId.remove(a4cDeploymentPaasId);
      statusByA4cDeploymentId.remove(dds.getA4cDeploymentId());
      statusByOrchestratorDeploymentUuid.remove(dds.getOrchestratorDeploymentUuid());      
    }
  }
  
  public synchronized DeepDeploymentStatus getStatusByA4cDeploymentId(String a4cDeploymentId) {
    return statusByA4cDeploymentId.get(a4cDeploymentId);
  }
  
  public synchronized void updateStatusByA4cDeploymentId(String a4cDeploymentId,
      Throwable error,  DeploymentStatus deploymentStatus) {
    DeepDeploymentStatus dds = statusByA4cDeploymentId.get(a4cDeploymentId);
    if (dds != null)
      dds.setStatus(deploymentStatus);
  }
  
  public synchronized void removeStatusByA4cDeploymentId(String a4cDeploymentId) {
    DeepDeploymentStatus dds = statusByA4cDeploymentId.get(a4cDeploymentId);
    if (dds != null) {
      statusByA4cDeploymentPaasId.remove(dds.getA4cDeploymentPaasId());
      statusByA4cDeploymentId.remove(a4cDeploymentId);
      statusByOrchestratorDeploymentUuid.remove(dds.getOrchestratorDeploymentUuid());      
    }
  }
  
  public synchronized DeepDeploymentStatus getStatusByOrchestratorDeploymentUuid(String orchestratorDeploymentUuid) {
    return statusByOrchestratorDeploymentUuid.get(orchestratorDeploymentUuid);
  }
  
  public synchronized void updateStatusByOrchestratorDeploymentUuid(String orchestratorDeploymentUuid,
      Throwable error, DeploymentStatus deploymentStatus) {
    DeepDeploymentStatus dds = statusByOrchestratorDeploymentUuid.get(orchestratorDeploymentUuid);
    if (dds != null)
      dds.setStatus(deploymentStatus);
  }
  
  public synchronized void removeStatusByOrchestratorDeploymentUuid(String orchestratorDeploymentUuid) {
    DeepDeploymentStatus dds = statusByOrchestratorDeploymentUuid.get(orchestratorDeploymentUuid);
    if (dds != null) {
      statusByA4cDeploymentPaasId.remove(dds.getA4cDeploymentPaasId());
      statusByA4cDeploymentId.remove(dds.getA4cDeploymentId());
      statusByOrchestratorDeploymentUuid.remove(orchestratorDeploymentUuid);      
    }
  }
  
  public synchronized void addDeepDeploymentStatus(String a4cDeploymentPaasId, String a4cDeploymentId,
      String orchestratorDeploymentUuid, String orchestratorId, Throwable error, DeploymentStatus status) {
    DeepDeploymentStatus dds = new DeepDeploymentStatus(a4cDeploymentPaasId, a4cDeploymentId,
        orchestratorDeploymentUuid, orchestratorId, error, status);
    statusByA4cDeploymentPaasId.put(a4cDeploymentPaasId, dds);
    statusByA4cDeploymentId.put(a4cDeploymentId, dds);
    statusByOrchestratorDeploymentUuid.put(orchestratorDeploymentUuid, dds);
    
  }
  
  public synchronized final Collection<DeepDeploymentStatus> getActiveDeployments() {
    return statusByA4cDeploymentPaasId.values().stream()
        .filter(dds -> dds.getStatus() != DeploymentStatus.UNDEPLOYED)
        .collect(Collectors.toList());
  }
  
}
