package es.upv.indigodc.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Node extends AbstractatNode {

    private String id;

    private String deploymentId;

    private Map<String, Object> properties;

    private String blueprintId;

    private int numberOfInstances;

    private int deployNumberOfInstances;

    private String hostId;

    private Set<String> typeHierarchy;

    private String type;

    private List<Relationship> relationships;
}
