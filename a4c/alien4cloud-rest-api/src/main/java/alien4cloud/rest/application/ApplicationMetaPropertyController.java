package alien4cloud.rest.application;

import javax.annotation.Resource;
import javax.inject.Inject;

import alien4cloud.model.common.IMetaProperties;
import alien4cloud.rest.common.AbstractMetaPropertyController;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import alien4cloud.audit.annotation.Audit;
import alien4cloud.common.MetaPropertiesService;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.model.application.Application;
import alien4cloud.rest.internal.model.PropertyRequest;
import alien4cloud.rest.model.RestErrorBuilder;
import alien4cloud.rest.model.RestErrorCode;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.rest.model.RestResponseBuilder;
import alien4cloud.security.AuthorizationUtil;
import alien4cloud.security.model.ApplicationRole;
import alien4cloud.tosca.properties.constraints.ConstraintUtil;
import org.alien4cloud.tosca.exceptions.ConstraintValueDoNotMatchPropertyTypeException;
import org.alien4cloud.tosca.exceptions.ConstraintViolationException;

import io.swagger.annotations.Api;

@Slf4j
@RestController
@RequestMapping({"/rest/applications/{applicationId:.+}/properties", "/rest/v1/applications/{applicationId:.+}/properties", "/rest/latest/applications/{applicationId:.+}/properties"})
@Api(value = "", description = "Operations on Application's meta-properties")
public class ApplicationMetaPropertyController extends AbstractMetaPropertyController<Application> {

    /**
     * Update or create a property for an application
     *
     * @param applicationId id of the application
     * @param propertyRequest property request
     * @return information on the constraint
     * @throws ConstraintValueDoNotMatchPropertyTypeException
     * @throws ConstraintViolationException
     */
    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Audit
    public RestResponse<ConstraintUtil.ConstraintInformation> upsertProperty(@PathVariable String applicationId,
            @RequestBody PropertyRequest propertyRequest)
                    throws ConstraintViolationException, ConstraintValueDoNotMatchPropertyTypeException {
        return super.upsertProperty(applicationId, propertyRequest);
    }

    @Override
    protected Application getTarget(String applicationId) {
        Application application =  alienDAO.findById(Application.class, applicationId);
        AuthorizationUtil.checkAuthorizationForApplication(application, ApplicationRole.APPLICATION_MANAGER);
        return application;
    }
}