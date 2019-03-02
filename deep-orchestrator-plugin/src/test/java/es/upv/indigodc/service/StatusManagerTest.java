package es.upv.indigodc.service;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import alien4cloud.paas.model.DeploymentStatus;
import es.upv.indigodc.service.model.AlienDeploymentMapping;
import es.upv.indigodc.service.model.OrchestratorDeploymentMapping;
import es.upv.indigodc.service.model.PluginDeploymentStatus;

public class StatusManagerTest {
	
	@Test
	/**
	 * Add deployment and then retrieve it
	 */
	public void addDeploymentRetrieveIt() {
	  StatusManager ms = new StatusManager();
	  ms.init();
		ms.addDeepDeploymentStatus("a4cDeploymentPaasId", "alienDeploymentId", "orchestratorUuidDeployment", 
		    "orchestratorId", "deployerUsername", null, DeploymentStatus.DEPLOYED);
		PluginDeploymentStatus<DeploymentStatus> odm = ms.getStatusByA4cDeploymentId("alienDeploymentId");
		Assertions.assertEquals(odm.getOrchestratorDeploymentUuid(), "orchestratorUuidDeployment");
		PluginDeploymentStatus<DeploymentStatus> adm = ms.getStatusByOrchestratorDeploymentUuid("orchestratorUuidDeployment");
		Assertions.assertEquals(adm.getA4cDeploymentId(), "alienDeploymentId");
		Assertions.assertEquals(adm.getOrchestratorId(), "orchestratorId");
	}
}
