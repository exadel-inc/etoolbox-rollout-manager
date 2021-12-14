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
import com.day.cq.wcm.msm.api.LiveCopy;
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
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.servlet.Servlet;
import java.util.Optional;

/**
 * Collects data related to eligible for synchronization live relationships for the given resource.
 * The data is necessary for building 'Targets' tree in the UI dialog and further rollout in {@link RolloutServlet}
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "/apps/etoolbox-rollout-manager/collect-live-copies",
        methods = HttpConstants.METHOD_POST
)
@ServiceDescription("The servlet for collecting live copies")
public class CollectLiveCopiesServlet extends SlingAllMethodsServlet {
    private static final Logger LOG = LoggerFactory.getLogger(CollectLiveCopiesServlet.class);

    private static final String PATH_REQUEST_PARAM = "path";

    private static final String MASTER_JSON_FIELD = "master";
    private static final String PATH_JSON_FIELD = "path";
    private static final String DEPTH_JSON_FIELD = "depth";
    private static final String LIVE_COPIES_JSON_FIELD = "liveCopies";
    private static final String IS_NEW_JSON_FIELD = "isNew";

    @Reference
    private transient LiveRelationshipManager liveRelationshipManager;

    @Reference
    private transient RelationshipCheckerService relationshipCheckerService;

    @Override
    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response) {
        String path = ServletUtil.getRequestParamString(request, PATH_REQUEST_PARAM);
        if (StringUtils.isBlank(path)) {
            response.setStatus(HttpStatus.SC_BAD_REQUEST);
            LOG.warn("Path is blank, live copies collection failed");
            return;
        }
        String jsonResponse = getLiveCopiesJsonArray(path, StringUtils.EMPTY, request.getResourceResolver(), 0)
                .toString();
        ServletUtil.writeJsonResponse(response, jsonResponse);
    }

    private JsonArray getLiveCopiesJsonArray(String source,
                                             String sourceSyncPath,
                                             ResourceResolver resourceResolver,
                                             int depth) {
        Optional<Resource> sourceResource = Optional.ofNullable(resourceResolver.getResource(source));
        if (!sourceResource.isPresent()) {
            return JsonValue.EMPTY_JSON_ARRAY;
        }
        JsonArrayBuilder responseArrayBuilder = Json.createArrayBuilder();
        try {
            RangeIterator relationships =
                    liveRelationshipManager.getLiveRelationships(sourceResource.get(), null, null);
            while (relationships.hasNext()) {
                LiveRelationship relationship = (LiveRelationship) relationships.next();
                JsonObject liveCopyJson = relationshipToJson(relationship, source, sourceSyncPath, depth, resourceResolver);
                if (!liveCopyJson.isEmpty()) {
                    responseArrayBuilder.add(liveCopyJson);
                }
            }
        } catch (WCMException e) {
            LOG.error("Live copies collecting failed", e);
        }
        return responseArrayBuilder.build();
    }

    private JsonObject relationshipToJson(LiveRelationship relationship,
                                          String source,
                                          String sourceSyncPath,
                                          int depth,
                                          ResourceResolver resourceResolver) {
        String syncPath = buildSyncPath(relationship, sourceSyncPath);
        String targetPath = buildTargetPath(relationship, syncPath);

        LiveCopy liveCopy = relationship.getLiveCopy();
        if (liveCopy == null
                || (StringUtils.isNotBlank(syncPath) && !liveCopy.isDeep())
                || !relationshipCheckerService.isAvailableForSync(syncPath, targetPath, liveCopy.getExclusions(), resourceResolver)) {
            return JsonValue.EMPTY_JSON_OBJECT;
        }

        String liveCopyPath = liveCopy.getPath();
        return Json.createObjectBuilder()
                .add(MASTER_JSON_FIELD, source + sourceSyncPath)
                .add(PATH_JSON_FIELD, liveCopyPath + syncPath)
                .add(DEPTH_JSON_FIELD, depth)
                .add(LIVE_COPIES_JSON_FIELD, getLiveCopiesJsonArray(liveCopyPath, syncPath, resourceResolver, depth + 1))
                .add(IS_NEW_JSON_FIELD, !resourceExists(resourceResolver, liveCopyPath + syncPath))
                .build();
    }

    private String buildSyncPath(LiveRelationship relationship, String sourceSyncPath) {
        return Optional.ofNullable(relationship.getSyncPath())
                .filter(StringUtils::isNotBlank)
                .orElse(sourceSyncPath);
    }

    private String buildTargetPath(LiveRelationship relationship, String syncPath) {
        String targetPath = relationship.getTargetPath();
        if (StringUtils.isBlank(targetPath)) {
            return syncPath;
        }
        return targetPath.contains(syncPath) ? targetPath : targetPath + syncPath;
    }

    private boolean resourceExists(ResourceResolver resourceResolver, String path) {
        return Optional.ofNullable(resourceResolver.getResource(path))
                .isPresent();
    }
}