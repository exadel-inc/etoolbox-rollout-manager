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

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;
import com.day.cq.wcm.msm.api.RolloutManager;
import com.exadel.etoolbox.rolloutmanager.core.models.RolloutItem;
import com.exadel.etoolbox.rolloutmanager.core.models.RolloutStatus;
import com.exadel.etoolbox.rolloutmanager.core.services.PageReplicationService;
import com.exadel.etoolbox.rolloutmanager.core.servlets.util.ServletUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.servlet.Servlet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Performs rollout based on input json array. The 'isDeepRollout' request parameter defines if child pages should be
 * included in the rollout process. The json array is mapped to the array of {@link RolloutItem}. The rollout items
 * array is then grouped by depth which defines a level of nesting for live relationships. The items with a lower depth
 * are rolled out first. Rollout operation returns a {@link List} of {@link RolloutStatus} items. Failed items are put
 * into the servlet response and outputted in the UI dialog.
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "/apps/etoolbox-rollout-manager/rollout",
        methods = HttpConstants.METHOD_POST
)
@ServiceDescription("The servlet for collecting live copies")
public class RolloutServlet extends SlingAllMethodsServlet {
    private static final Logger LOG = LoggerFactory.getLogger(RolloutServlet.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String SELECTION_JSON_ARRAY_PARAM = "selectionJsonArray";
    private static final String IS_DEEP_ROLLOUT_PARAM = "isDeepRollout";
    private static final String SHOULD_ACTIVATE_PARAM = "shouldActivate";
    private static final String FAILED_TARGETS_RESPONSE_PARAM = "failedTargets";

    @Reference
    private transient RolloutManager rolloutManager;

    @Reference
    private transient PageReplicationService pageReplicationService;

    @Override
    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response) {
        StopWatch sw = StopWatch.createStarted();
        LOG.debug("Starting rollout of selected items");

        String selectionJsonArray = ServletUtil.getRequestParamString(request, SELECTION_JSON_ARRAY_PARAM);
        if (StringUtils.isBlank(selectionJsonArray)) {
            response.setStatus(HttpStatus.SC_BAD_REQUEST);
            LOG.warn("Selection json array is blank, rollout failed");
            return;
        }
        LOG.debug("Selection data: {}", selectionJsonArray);

        RolloutItem[] rolloutItems = jsonArrayToRolloutItems(selectionJsonArray);
        if (ArrayUtils.isEmpty(rolloutItems)) {
            response.setStatus(HttpStatus.SC_BAD_REQUEST);
            LOG.warn("Rollout items array is empty, rollout failed. Selected live copies json: {}", selectionJsonArray);
            return;
        }

        PageManager pageManager = request.getResourceResolver().adaptTo(PageManager.class);
        if (pageManager == null) {
            response.setStatus(HttpStatus.SC_BAD_REQUEST);
            LOG.warn("Page Manager is null, rollout failed. Selected live copies json: {}", selectionJsonArray);
            return;
        }

        boolean isDeepRollout = ServletUtil.getRequestParamBoolean(request, IS_DEEP_ROLLOUT_PARAM);
        LOG.debug("Is deep rollout (include subpages): {}", isDeepRollout);

        List<RolloutStatus> rolloutStatuses = doItemsRollout(rolloutItems, pageManager, isDeepRollout);

        boolean shouldActivate = ServletUtil.getRequestParamBoolean(request, SHOULD_ACTIVATE_PARAM);
        LOG.debug("Should activate pages: {}", shouldActivate);
        List<RolloutStatus> activationStatuses = new ArrayList<>();
        if (shouldActivate) {
            activationStatuses = pageReplicationService.replicateItems(request.getResourceResolver(), rolloutItems, request.getResourceResolver().adaptTo(PageManager.class));
        }

        writeStatusesIfFailed(Stream.concat(rolloutStatuses.stream(), activationStatuses.stream())
                .collect(Collectors.toList()), response);
        LOG.debug("Rollout of selected items is completed in {} ms", sw.getTime(TimeUnit.MILLISECONDS));
    }

    private void writeStatusesIfFailed(List<RolloutStatus> rolloutStatuses, SlingHttpServletResponse response) {
        List<String> failedTargets = rolloutStatuses.stream()
                .filter(status -> !status.isSuccess())
                .map(RolloutStatus::getTarget)
                .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(failedTargets)) {
            LOG.debug("Rollout failed for the following targets: {}", failedTargets);
            response.setStatus(HttpStatus.SC_BAD_REQUEST);
            String jsonResponse = Json.createObjectBuilder()
                    .add(FAILED_TARGETS_RESPONSE_PARAM, Json.createArrayBuilder(failedTargets))
                    .build()
                    .toString();
            ServletUtil.writeJsonResponse(response, jsonResponse);
        }
    }

    private List<RolloutStatus> doItemsRollout(RolloutItem[] items, PageManager pageManager, boolean isDeep) {
        return Arrays.stream(items)
                .collect(Collectors.groupingBy(RolloutItem::getDepth))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .flatMap(sortedByDepthItems -> rolloutSortedByDepthItems(sortedByDepthItems, pageManager, isDeep))
                .collect(Collectors.toList());
    }

    private Stream<RolloutStatus> rolloutSortedByDepthItems(List<RolloutItem> items, PageManager pageManager, boolean isDeep) {
        return items.stream()
                .filter(item -> !skipAutoTriggered(item))
                .filter(item -> StringUtils.isNotBlank(item.getTarget()))
                .map(item -> rollout(item, pageManager, isDeep));
    }

    private boolean skipAutoTriggered(RolloutItem item) {
        boolean skipAutoTriggered = item.getDepth() != 0 && item.isAutoRolloutTrigger();
        if (skipAutoTriggered) {
            LOG.debug("Item rollout skipped due to auto trigger, master: {}, target: {}", item.getMaster(), item.getTarget());
        }
        return skipAutoTriggered;
    }

    private RolloutStatus rollout(RolloutItem targetItem, PageManager pageManager, boolean isDeep) {
        String targetPath = targetItem.getTarget();
        RolloutStatus status = new RolloutStatus(targetPath);

        String masterPath = targetItem.getMaster();
        Optional<Page> masterPage = Optional.ofNullable(pageManager.getPage(masterPath));
        if (!masterPage.isPresent()) {
            status.setSuccess(false);
            LOG.warn("Rollout failed - master page is null, master page path: {}", masterPath);
            return status;
        }

        RolloutManager.RolloutParams params = toRolloutParams(masterPage.get(), targetPath, isDeep);
        try {
            LOG.debug("Item rollout started, master: {}, target: {}", masterPath, targetPath);
            rolloutManager.rollout(params);
            status.setSuccess(true);
            LOG.debug("Item rollout completed, master: {}, target: {}", masterPath, targetPath);
        } catch (WCMException e) {
            status.setSuccess(false);
            String message = String.format("Item rollout failed, master: %s, target: %s", masterPath, targetPath);
            LOG.error(message, e);
        }
        return status;
    }

    private RolloutManager.RolloutParams toRolloutParams(Page masterPage, String targetPath, boolean isDeep) {
        RolloutManager.RolloutParams params = new RolloutManager.RolloutParams();
        params.master = masterPage;
        params.targets = new String[]{targetPath};
        params.isDeep = isDeep;
        params.trigger = RolloutManager.Trigger.ROLLOUT;
        return params;
    }

    private RolloutItem[] jsonArrayToRolloutItems(String jsonArray) {
        try {
            return OBJECT_MAPPER.readValue(jsonArray, RolloutItem[].class);
        } catch (IOException e) {
            LOG.error("Failed to map json to models", e);
        }
        return new RolloutItem[0];
    }
}