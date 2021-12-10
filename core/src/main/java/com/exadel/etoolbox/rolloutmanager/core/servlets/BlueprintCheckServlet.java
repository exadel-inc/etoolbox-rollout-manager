/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exadel.etoolbox.rolloutmanager.core.servlets;

import com.day.cq.wcm.api.WCMException;
import com.day.cq.wcm.msm.api.LiveRelationship;
import com.day.cq.wcm.msm.api.LiveRelationshipManager;
import com.exadel.etoolbox.rolloutmanager.core.services.RelationshipCheckerService;
import com.exadel.etoolbox.rolloutmanager.core.servlets.util.ServletUtil;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RangeIterator;
import javax.json.Json;
import javax.servlet.Servlet;
import java.util.Optional;

@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "/apps/etoolbox-rollout-manager/blueprint-check",
        methods = HttpConstants.METHOD_POST
)
@ServiceDescription("The servlet for checking if page is blueprint")
public class BlueprintCheckServlet extends SlingAllMethodsServlet {
    private static final Logger LOG = LoggerFactory.getLogger(BlueprintCheckServlet.class);

    private static final String PATH_REQUEST_PARAM = "path";
    private static final String IS_AVAILABLE_FOR_ROLLOUT_PARAM = "isAvailableForRollout";

    @Reference
    private transient LiveRelationshipManager liveRelationshipManager;

    @Reference
    private transient RelationshipCheckerService relationshipCheckerService;

    @Override
    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response) {
        String path = ServletUtil.getRequestParamString(request, PATH_REQUEST_PARAM);
        if (StringUtils.isBlank(path)) {
            response.setStatus(HttpStatus.SC_BAD_REQUEST);
            LOG.warn("Path is blank, blueprint check failed");
            return;
        }

        ResourceResolver resourceResolver = request.getResourceResolver();

        Optional<Resource> sourceResource = Optional.ofNullable(resourceResolver.getResource(path));
        if (!sourceResource.isPresent()) {
            response.setStatus(HttpStatus.SC_BAD_REQUEST);
            LOG.warn("Source resource is null, rollout availability check failed, path: {}", path);
            return;
        }

        String jsonResponse = Json.createObjectBuilder()
                .add(IS_AVAILABLE_FOR_ROLLOUT_PARAM, isAvailableForRollout(sourceResource.get(), resourceResolver))
                .build()
                .toString();
        ServletUtil.writeJsonResponse(response, jsonResponse);
    }

    private boolean isAvailableForRollout(Resource sourceResource, ResourceResolver resourceResolver) {
        try {
            RangeIterator relationships =
                    liveRelationshipManager.getLiveRelationships(sourceResource, null, null);
            while (relationships.hasNext()) {
                LiveRelationship relationship = (LiveRelationship) relationships.next();
                if (relationshipCheckerService.isAvailableForSync(relationship, resourceResolver)) {
                    return true;
                }
            }
        } catch (WCMException e) {
            LOG.error("Blueprint check failed", e);
        }
        return false;
    }
}