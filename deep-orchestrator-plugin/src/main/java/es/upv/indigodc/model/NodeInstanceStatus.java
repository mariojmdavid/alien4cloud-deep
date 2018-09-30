package es.upv.indigodc.model;

import alien4cloud.paas.model.InstanceStatus;

public class NodeInstanceStatus  extends AbstractatNode {
  
  public static enum Status {
    UNINITIALIZED, CREATING, CREATED, CONFIGURING, CONFIGURED, STARTING,
    STARTED, STOPPING, STOPPED, DELETING, DELETED
    
  }

    public static InstanceStatus getInstanceStatusFromState(String status) {
        switch (NodeInstanceStatus.Status.valueOf(status.toUpperCase())) {
            case STARTED:
                return InstanceStatus.SUCCESS;
            case UNINITIALIZED:
            case STOPPING:
            case STOPPED:
            case STARTING:
            case CONFIGURING:
            case CONFIGURED:
            case CREATING:
            case CREATED:
            case DELETING:
                return InstanceStatus.PROCESSING;
            case DELETED:
                return null;
            default:
                return InstanceStatus.FAILURE;
        }
    }
}
