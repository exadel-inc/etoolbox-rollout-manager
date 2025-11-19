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

import com.exadel.etoolbox.rolloutmanager.core.models.RolloutItem;
import com.exadel.etoolbox.rolloutmanager.core.services.impl.RolloutExecutor;
import com.exadel.etoolbox.rolloutmanager.core.utils.RolloutLogUtil;
import com.exadel.etoolbox.rolloutmanager.core.utils.RolloutPlanUtil;
import com.exadel.etoolbox.rolloutmanager.core.utils.ServletUtil;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.servlet.Servlet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Initiates a rollout based on the provided JSON array. The {@code isDeepRollout} request parameter defines if child
 * pages should be included in the rollout process
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "/apps/etoolbox-rollout-manager/rollout",
        methods = HttpConstants.METHOD_POST
)
@ServiceDescription("Marshals on-demand rollout tasks")
public class RolloutServlet extends SlingAllMethodsServlet {
    private static final Logger LOG = LoggerFactory.getLogger(RolloutServlet.class);

    private static final String PARAM_IS_DEEP = "isDeepRollout";
    private static final String PARAM_SELECTION_JSON_ARRAY = "selectionJsonArray";
    private static final String PARAM_SHOULD_ACTIVATE = "shouldActivate";

    private static final String ERROR_COULD_NOT_CREATE = "Could not create a rollout task";
    static final String ERROR_MISSING_USER = "Cannot retrieve the current user";
    private static final String ERROR_MISSING_PLAN = "Rollout plan is missing or invalid";

    @Reference
    private transient JobManager jobManager;

    @Override
    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response) {
        String rolloutPlanSource = ServletUtil.getRequestParamString(request, PARAM_SELECTION_JSON_ARRAY);
        if (StringUtils.isBlank(rolloutPlanSource)) {
            ServletUtil.writeError(response, HttpStatus.SC_BAD_REQUEST, ERROR_MISSING_PLAN);
            return;
        }
        LOG.debug("Received rollout plan: {}", rolloutPlanSource);

        RolloutItem[] rolloutItems = RolloutPlanUtil.getItems(rolloutPlanSource);
        if (ArrayUtils.isEmpty(rolloutItems)) {
            ServletUtil.writeError(response, HttpStatus.SC_BAD_REQUEST, ERROR_MISSING_PLAN);
            LOG.warn("Could not extract rollout plan items. Provided content was as follows: {}", rolloutPlanSource);
            return;
        }

        String userId = ServletUtil.getUserId(request);
        if (StringUtils.isEmpty(userId)) {
            ServletUtil.writeError(response, HttpStatus.SC_BAD_REQUEST, ERROR_MISSING_USER);
            return;
        }

        Map<String, Object> jobProperties = new HashMap<>();
        jobProperties.put(RolloutExecutor.PROPERTY_ACTIVATE, ServletUtil.getRequestParamBoolean(request, PARAM_SHOULD_ACTIVATE));
        jobProperties.put(RolloutExecutor.PROPERTY_DEEP, ServletUtil.getRequestParamBoolean(request, PARAM_IS_DEEP));
        jobProperties.put(RolloutExecutor.PROPERTY_PLAN, rolloutPlanSource);
        jobProperties.put(RolloutExecutor.PROPERTY_USER, userId);
        jobProperties.put(RolloutExecutor.PROPERTY_PRE_LOG, getPreLog(rolloutPlanSource));
        Job job = jobManager.addJob(RolloutExecutor.TOPIC, jobProperties);
        if (job == null) {
            ServletUtil.writeError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, ERROR_COULD_NOT_CREATE);
            LOG.error(ERROR_COULD_NOT_CREATE);
            return;
        }

        response.setStatus(HttpStatus.SC_CREATED);
        ServletUtil.writeJsonResponse(response, Json.createObjectBuilder().add("task", job.getId()).build().toString());
    }

    private static String getPreLog(String rolloutPlanSource) {
        RolloutItem[] items = RolloutPlanUtil.getItems(rolloutPlanSource);
        if (ArrayUtils.isEmpty(items)) {
            return StringUtils.EMPTY;
        }
        assert items != null;
        AtomicReference<String> result = new AtomicReference<>();
        RolloutLogUtil.logTargets(
            result::set,
            Arrays.stream(items).sorted().map(RolloutItem::getTarget).collect(Collectors.toList()));
        return result.get();
    }
}
