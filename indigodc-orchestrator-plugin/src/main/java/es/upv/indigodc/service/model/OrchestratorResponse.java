package es.upv.indigodc.service.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpMethod;

/**
 * Holds the response received after a call to the Orchestrator.
 *
 * @author asalic
 */
@Getter
public class OrchestratorResponse {

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private ObjectMapper objectMapper;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private JsonNode rootResponse;

  /**
   * Build a response object.
   *
   * @param code The code returned by the server
   * @param callMethod The type of the method (GET, POST, PUT, DELETE, etc)
   * @param response The actual response from the server
   * @throws JsonProcessingException when trying to parse the response that should be a valid JSON
   * @throws IOException when trying to parse the response that should be a valid JSON
   */
  public OrchestratorResponse(int code, HttpMethod callMethod, StringBuilder response)
      throws JsonProcessingException, IOException {
    this.code = code;
    this.callMethod = callMethod;
    this.response = response;
    objectMapper = new ObjectMapper();
    objectMapper.enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    if (response.length() > 0) {
      rootResponse = objectMapper.readTree(response.toString());
    } else {
      rootResponse = objectMapper.createObjectNode();
    }
  }

  /** The return code of the call, e.g. 200 for OK */
  private int code;
  /** One of the calls methods for HTTP, e.g. GET, POST etc. used to get this response */
  private HttpMethod callMethod;
  /** The response itself with all its fields. */
  private StringBuilder response;

  /**
   * Checks the code returned by the server.
   *
   * @return true if the code is between 200 and 299, false otherwise
   */
  public boolean isCodeOk() {
    return code >= 200 && code <= 299;
  }

  /**
   * Extract the outputs from the response.
   *
   * @return A map between the output name and its value
   * @throws JsonProcessingException when trying to parse the response
   * @throws IOException when trying to parse the response
   */
  public Map<String, String> getOutputs() throws JsonProcessingException, IOException {
    Map<String, String> result = new HashMap<>();
    List<JsonNode> outputsList = getNodesByKey("outputs");
    if (outputsList != null && outputsList.size() > 0) {
      JsonNode output = outputsList.get(0);
      Iterator<Entry<String, JsonNode>> outputFields = output.fields();
      while (outputFields.hasNext()) {
        Entry<String, JsonNode> field = outputFields.next();
        result.put(field.getKey(),
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(field.getValue()));
      }
      // return objectMapper.convertValue(outputs.get(0), new TypeReference<Map<String, String>>()
      // {});
    }
    return result;
  }

  /**
   * Extract the UUID of the deployment as generated by the orchestrator.
   *
   * @return the UUID of the deployment
   * @throws JsonProcessingException Json Exception when parsing response
   * @throws IOException Exception when instantiating the JSON parser
   */
  public String getOrchestratorUuidDeployment() throws JsonProcessingException, IOException {
    List<JsonNode> vals = rootResponse.findValues("uuid");
    if (vals.size() > 0) {
      return vals.get(0).asText();
    } else {
      return null;
    }
  }

  /**
   * Extracts the status from the response from the orchestrator.
   *
   * @return the status as a string
   * @throws JsonProcessingException Json Exception when parsing response
   * @throws IOException Exception when instantiating the JSON parser
   */
  public String getStatusTopologyDeployment() throws JsonProcessingException, IOException {
    List<JsonNode> vals = rootResponse.findValues("status");
    if (vals.size() > 0) {
      return vals.get(0).asText();
    } else {
      return null;
    }
  }

  protected List<JsonNode> getNodesByKey(String key) throws JsonProcessingException, IOException {
    return rootResponse.findValues(key);
  }
}