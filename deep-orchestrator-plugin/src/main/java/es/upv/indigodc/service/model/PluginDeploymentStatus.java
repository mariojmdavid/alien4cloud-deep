package es.upv.indigodc.service.model;

import java.io.Serializable;

import org.apache.commons.lang.SerializationUtils;

import com.google.gwt.core.shared.SerializableThrowable;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter 
public class PluginDeploymentStatus<TYPE_STATUS extends Serializable > extends DeployedTopologyInformation implements Cloneable{
  
  /** Error that might be generated when obtaining the status; null means no error. */
  protected final SerializableThrowable error;
  
  /** The status of the deployment at a given time (when the call to get the info was executed). */
  protected final TYPE_STATUS status;
  
  public boolean hasError() {
    return error != null;
  }
  
  
  public PluginDeploymentStatus(String a4cDeploymentPaasId,  
    String a4cDeploymentId,
    
    String orchestratorDeploymentUuid,
    String orchestratorId,
    
    String deployerUsername, Throwable error, TYPE_STATUS status) {
    super(a4cDeploymentPaasId, a4cDeploymentId, orchestratorDeploymentUuid,
        orchestratorId, deployerUsername);
    if (error != null)
      this.error = (SerializableThrowable) SerializationUtils.clone(SerializableThrowable.fromThrowable(error));
    else
      this.error = null;
    this.status = (TYPE_STATUS) SerializationUtils.clone(status);
    
  }
  
  public PluginDeploymentStatus(PluginDeploymentStatus<TYPE_STATUS> pluginDeploymentStatus) {
    super(pluginDeploymentStatus.getA4cDeploymentId(), 
        pluginDeploymentStatus.getA4cDeploymentPaasId(), 
        pluginDeploymentStatus.getOrchestratorDeploymentUuid(),
        pluginDeploymentStatus.getOrchestratorId(),
        pluginDeploymentStatus.getDeployerUsername());
    if (pluginDeploymentStatus.getError() != null)
      this.error = (SerializableThrowable) SerializationUtils.clone(pluginDeploymentStatus.getError());
    else
      this.error = null;
    this.status = (TYPE_STATUS) SerializationUtils.clone(pluginDeploymentStatus.getStatus());
  }
  
//  /**
//   * The id of the whole deployment (basically the instance if the topology launched at one time on
//   * the orchestrator).
//   */
//  protected final String a4cDeploymentPaasId;
//  
//  protected final String a4cDeploymentId;
//  
//  protected final String orchestratorDeploymentUuid;
//  
//  /**
//   * The id of the orchestrator on which the deployment with the id {@link #deploymentId} has been
//   * launched.
//   */
//  protected final String orchestratorId;
//  
//  protected final String deployerUsername;
  


}
