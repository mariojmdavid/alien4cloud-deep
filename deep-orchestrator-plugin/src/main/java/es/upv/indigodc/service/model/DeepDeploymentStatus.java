package es.upv.indigodc.service.model;

import alien4cloud.paas.model.DeploymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** Utility class to store mapping between an AlienDeployment and Marathon. */
@Getter 
@AllArgsConstructor
public class DeepDeploymentStatus {

//  public static final AlienDeploymentMapping EMPTY = new AlienDeploymentMapping(
//      "unknown_deployment_id", "unknown_orchestrator_id", DeploymentStatus.UNKNOWN);

  /**
   * The id of the whole deployment (basically the instance if the topology launched at one time on
   * the orchestrator).
   */
  protected String a4cDeploymentPaasId;
  
  protected String a4cDeploymentId;
  
  protected String orchestratorDeploymentUuid;
  
  /**
   * The id of the orchestrator on which the deployment with the id {@link #deploymentId} has been
   * launched.
   */
  protected String orchestratorId;
  
  /** Error that might be generated when obtaining the status; null means no error. */
  @Setter
  protected Throwable error;
  
  /** The status of the deployment at a given time (when the call to get the info was executed). */
  @Setter
  protected DeploymentStatus status;
  
  public boolean hasError() {
    return error != null;
  }
}
