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
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.servlet.Servlet;
import java.util.Optional;

@Component(service = {Servlet.class})
@SlingServletResourceTypes(
        resourceTypes = "/bin/etoolbox/rollout-manager/collect-live-copies",
        methods = HttpConstants.METHOD_POST
)
@ServiceDescription("The servlet for collecting live copies")
public class CollectLiveCopiesServlet extends SlingAllMethodsServlet {
    private static final Logger LOG = LoggerFactory.getLogger(CollectLiveCopiesServlet.class);

    private static final String PATH_PARAM = "path";

    @Reference
    private transient LiveRelationshipManager liveRelationshipManager;

    @Override
    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response) {
        String path = ServletUtil.getRequestParamString(request, PATH_PARAM);
        if (StringUtils.isBlank(path)) {
            response.setStatus(HttpStatus.SC_BAD_REQUEST);
            LOG.warn("Path is blank, live copies collection failed");
            return;
        }
        String jsonResponse = getLiveCopiesJsonArray(path, request.getResourceResolver(), 0)
                .toString();
        ServletUtil.writeJsonResponse(response, jsonResponse);
    }

    private JsonArray getLiveCopiesJsonArray(String bluePrintPath, ResourceResolver resourceResolver, int depth) {
        Optional<Resource> blueprintResource = Optional.ofNullable(resourceResolver.getResource(bluePrintPath));
        if (!blueprintResource.isPresent()) {
            return JsonValue.EMPTY_JSON_ARRAY;
        }
        JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
        try {
            RangeIterator relationships =
                    liveRelationshipManager.getLiveRelationships(blueprintResource.get(), null, null);
            while (relationships.hasNext()) {
                LiveRelationship relationship = (LiveRelationship) relationships.next();
                String liveCopyPath = relationship.getLiveCopy().getPath();

                JsonObject liveCopyJson = Json.createObjectBuilder()
                        .add("master", bluePrintPath)
                        .add("path", liveCopyPath)
                        .add("depth", depth)
                        .add("liveCopies", getLiveCopiesJsonArray(liveCopyPath, resourceResolver, depth + 1))
                        .build();

                jsonArrayBuilder.add(liveCopyJson);
            }

        } catch (WCMException e) {
            LOG.error("live copies collection failed", e);
        }
        return jsonArrayBuilder.build();
    }
}