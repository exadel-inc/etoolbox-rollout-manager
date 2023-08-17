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
import com.day.cq.wcm.msm.api.RolloutConfig;
import com.day.cq.wcm.msm.api.RolloutManager;
import com.exadel.etoolbox.rolloutmanager.core.services.RelationshipCheckerService;
import com.exadel.etoolbox.rolloutmanager.core.servlets.util.ServletUtil;
import com.exadel.etoolbox.rolloutmanager.core.servlets.util.TimeUtil;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.json.XML;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.day.cq.wcm.msm.api.MSMNameConstants.PN_LAST_ROLLEDOUT;

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

    private static final String NOT_ROLLEDOUT_LABEL = "Not Rolledout";
    private static final String DATE_TIME_PATTERN = "HH:mm:ss z dd-MM-yyyy";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    private static final String PATH_REQUEST_PARAM = "path";

    private static final String MASTER_JSON_FIELD = "master";
    private static final String PATH_JSON_FIELD = "path";
    private static final String DEPTH_JSON_FIELD = "depth";
    private static final String LIVE_COPIES_JSON_FIELD = "liveCopies";
    private static final String IS_NEW_JSON_FIELD = "isNew";
    private static final String HAS_ROLLOUT_TRIGGER_JSON_FIELD = "autoRolloutTrigger";
    private static final String LAST_ROLLEDOUT_JSON_FIELD = "lastRolledout";
    private static final String LAST_ROLLEDOUT_TIME_AGO_JSON_FIELD = "lastRolledoutTimeAgo";

    @Reference
    private transient LiveRelationshipManager liveRelationshipManager;

    @Reference
    private transient RelationshipCheckerService relationshipCheckerService;

    @Override
    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response) {
        StopWatch sw = StopWatch.createStarted();
        LOG.debug("Starting live copies data collection for the selected page");

        String path = ServletUtil.getRequestParamString(request, PATH_REQUEST_PARAM);
        if (StringUtils.isBlank(path)) {
            response.setStatus(HttpStatus.SC_BAD_REQUEST);
            LOG.warn("Path is blank, live copies collection failed");
            return;
        }
        LOG.debug("Selected page path: {}", path);

        String jsonResponse = getLiveCopiesJsonArray(path, StringUtils.EMPTY, request.getResourceResolver(), 0)
                .toString();
        LOG.debug("Live copies data json: {}", jsonResponse);

        ServletUtil.writeJsonResponse(response, jsonResponse);
        LOG.debug("Live copies data collection is completed in {} ms", sw.getTime(TimeUnit.MILLISECONDS));
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
        boolean isNew = !resourceExists(resourceResolver, liveCopyPath + syncPath);

        return Json.createObjectBuilder()
                .add(MASTER_JSON_FIELD, source + sourceSyncPath)
                .add(PATH_JSON_FIELD, liveCopyPath + syncPath)
                .add(DEPTH_JSON_FIELD, depth)
                .add(LIVE_COPIES_JSON_FIELD, getLiveCopiesJsonArray(liveCopyPath, syncPath, resourceResolver, depth + 1))
                .add(IS_NEW_JSON_FIELD, isNew)
                .add(HAS_ROLLOUT_TRIGGER_JSON_FIELD, !isNew && hasAutoTrigger(liveCopy))
                .add(LAST_ROLLEDOUT_TIME_AGO_JSON_FIELD, getRolledoutAgoJsonField(resourceResolver, liveCopyPath + syncPath))
                .add(LAST_ROLLEDOUT_JSON_FIELD, getLastRolloutJsonField(resourceResolver, liveCopyPath + syncPath))
                .build();
    }

    private boolean hasAutoTrigger(LiveCopy liveCopy) {
        return liveCopy.getRolloutConfigs().stream()
                .map(RolloutConfig::getTrigger)
                .anyMatch(trigger -> trigger == RolloutManager.Trigger.MODIFICATION || trigger == RolloutManager.Trigger.ROLLOUT);
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

    private String getLastRolloutJsonField(ResourceResolver resourceResolver, String resourcePath) {
        String date = getStringDate(resourceResolver, resourcePath);
        ZonedDateTime dateTime = null;
        if (StringUtils.isNotEmpty(date)) {
            dateTime = ZonedDateTime.parse(date, DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(ZoneId.systemDefault());
        }

        return dateTime != null ? dateTime.format(DATE_TIME_FORMATTER) : StringUtils.EMPTY;
    }

    private String getRolledoutAgoJsonField(ResourceResolver resourceResolver, String resourcePath) {
        String date = getStringDate(resourceResolver, resourcePath);
        if (StringUtils.isEmpty(date)) {
            return NOT_ROLLEDOUT_LABEL;
        }
        return TimeUtil.timeSince(getStringDate(resourceResolver, resourcePath));
    }

    private static String getStringDate(ResourceResolver resourceResolver, String resourcePath) {
        Resource syncResource = resourceResolver.getResource(resourcePath + XML.SLASH + JcrConstants.JCR_CONTENT);
        ValueMap valueMap = syncResource != null ? syncResource.getValueMap() : null;
        return valueMap != null && valueMap.containsKey(PN_LAST_ROLLEDOUT) ? valueMap.get(PN_LAST_ROLLEDOUT, String.class) : StringUtils.EMPTY;
    }
}