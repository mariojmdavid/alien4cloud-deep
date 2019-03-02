package es.upv.indigodc.service.model;

import org.elasticsearch.annotation.ESObject;
import org.elasticsearch.annotation.query.TermFilter;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @AllArgsConstructor 
@ESObject
public class DeployedTopologyInformation implements Cloneable {
  
  /**
   * The id of the whole deployment (basically the instance if the topology launched at one time on
   * the orchestrator).
   */

  protected final String a4cDeploymentPaasId;

  protected final String a4cDeploymentId;

  @TermFilter
  protected final String orchestratorDeploymentUuid;
  
  /**
   * The id of the orchestrator on which the deployment with the id {@link #deploymentId} has been
   * launched.
   */
  protected final String orchestratorId;
  
  protected final String deployerUsername;

}
