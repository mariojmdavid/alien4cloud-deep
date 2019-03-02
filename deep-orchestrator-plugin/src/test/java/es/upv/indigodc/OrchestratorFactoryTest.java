package es.upv.indigodc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import es.upv.indigodc.configuration.CloudConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrchestratorFactoryTest {
	
//	@Test
//	public void testDefaultConfNoPrivateInfo() {
//		IndigoDcOrchestratorFactory fact = new IndigoDcOrchestratorFactory();
//		CloudConfiguration cc = fact.getDefaultConfiguration();
//
//		Assertions.assertEquals(cc.getClientId(), "none");
//		Assertions.assertEquals(cc.getClientSecret(), "none");
//	}
	
	@Test
	public void badPathToDefaultConf() throws NoSuchFieldException, SecurityException, Exception {		
		OrchestratorFactory fact = new IndigoDcOrchestratorFactoryBadPath();
		CloudConfiguration cc = fact.getDefaultConfiguration();
		Assertions.assertEquals(cc.getClientId(), OrchestratorFactory.NO_DEFAULT_CONF_FILE);
		Assertions.assertEquals(cc.getClientScopes(), OrchestratorFactory.NO_DEFAULT_CONF_FILE);
		Assertions.assertEquals(cc.getClientSecret(), OrchestratorFactory.NO_DEFAULT_CONF_FILE);
		Assertions.assertEquals(cc.getIamHost(), OrchestratorFactory.NO_DEFAULT_CONF_FILE);
		Assertions.assertEquals(cc.getIamHostCert(), OrchestratorFactory.NO_DEFAULT_CONF_FILE);
		Assertions.assertEquals(cc.getImportIndigoCustomTypes(), OrchestratorFactory.NO_DEFAULT_CONF_FILE);
		Assertions.assertEquals(cc.getOrchestratorEndpoint(), OrchestratorFactory.NO_DEFAULT_CONF_FILE);
		Assertions.assertEquals(cc.getOrchestratorEndpointCert(), OrchestratorFactory.NO_DEFAULT_CONF_FILE);
		Assertions.assertEquals(cc.getTokenEndpoint(), OrchestratorFactory.NO_DEFAULT_CONF_FILE);
		Assertions.assertEquals(cc.getOrchestratorEndpointCert(), OrchestratorFactory.NO_DEFAULT_CONF_FILE);
		Assertions.assertEquals(cc.getOrchestratorPollInterval(), OrchestratorFactory.NO_DEFAULT_CONF_FILE_POLL);
	}
	
	protected static class IndigoDcOrchestratorFactoryBadPath  extends OrchestratorFactory{
		
		@Override
		  protected String getCloudConfDefaultFile() {
			  return "fake";
		  }
	}

}
