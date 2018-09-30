package es.upv.indigodc.model;

import java.util.List;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeInstance  extends AbstractatNode{

    private String id;

    private String nodeId;

    private String hostId;

    private String deploymentId;

    private Map<String, Object> runtimeProperties;

    private String state;

    private List<RelationshipInstance> relationships;
}
