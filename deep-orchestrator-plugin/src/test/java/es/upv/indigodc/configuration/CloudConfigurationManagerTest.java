package es.upv.indigodc.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

import es.upv.indigodc.OrchestratorFactory;

public class CloudConfigurationManagerTest {
	
	@Test
	public void multipleOrchestratorsSameConf() {
		OrchestratorFactory fact = new OrchestratorFactory();
		CloudConfiguration cc = fact.getDefaultConfiguration();
		CloudConfigurationManager ccm = new CloudConfigurationManager();
		ccm.addCloudConfiguration("1", cc);
		ccm.addCloudConfiguration("2", cc);
		CloudConfiguration cc3 = fact.getDefaultConfiguration();
		cc3.setIamHost(OrchestratorFactory.NO_DEFAULT_CONF_FILE);
		ccm.addCloudConfiguration("3", cc3);
		assertEquals(ccm.getCloudConfiguration("3").getIamHost(), OrchestratorFactory.NO_DEFAULT_CONF_FILE);
		assertEquals(ccm.getCloudConfiguration("2"), cc);
		ccm.addCloudConfiguration("2", cc3);
		assertNotSame(ccm.getCloudConfiguration("2"), cc);
		assertEquals(ccm.getCloudConfiguration("2"), cc3);
	}

}
