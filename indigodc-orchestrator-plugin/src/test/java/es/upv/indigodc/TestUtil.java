package es.upv.indigodc;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.junit.Ignore;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import alien4cloud.security.model.User;
import es.upv.indigodc.configuration.CloudConfiguration;
import es.upv.indigodc.service.OrchestratorConnectorTest;

public class TestUtil {
  
  @Ignore("Not testing method")
  public static CloudConfiguration getRealConfiguration(String fileNameResource)
      throws JsonParseException, JsonMappingException, IOException {
    ObjectMapper mapper = new ObjectMapper();
    if (fileNameResource == null)
      fileNameResource = IndigoDcOrchestratorFactory.CLOUD_CONFIGURATION_DEFAULTS_FILE;
    InputStream is =
        IndigoDcOrchestratorFactory.class.getResourceAsStream(fileNameResource);
    return mapper.readValue(is, CloudConfiguration.class);
  }
  
  @Ignore("Not testing method")
  public static User getTestUser()
      throws JsonParseException, JsonMappingException, IOException {
    User u = new User();
    u.setUsername("testuser");
    u.setPassword("password");
    return u;
  }

  @Ignore("Not testing method")
  public static CloudConfiguration getTestConfiguration(String fileNameResource)
      throws JsonParseException, JsonMappingException, IOException {
    ObjectMapper mapper = new ObjectMapper();
    URL url = OrchestratorConnectorTest.class.getClassLoader().getResource(fileNameResource);
    InputStream is = new FileInputStream(url.getPath());
    CloudConfiguration cf = mapper.readValue(is, CloudConfiguration.class);
    return cf;
  }

}