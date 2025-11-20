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
import com.exadel.etoolbox.rolloutmanager.core.utils.ThrottledLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Reports status of rollout tasks
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "/apps/etoolbox-rollout-manager/rollout/status",
        methods = HttpConstants.METHOD_GET
)
@ServiceDescription("Reports status of rollout tasks")
public class RolloutStatusServlet extends SlingSafeMethodsServlet {
    private static final Logger LOG = LoggerFactory.getLogger(RolloutStatusServlet.class);

    private static final int NO_LIMIT = 0;
    private static final Map<String,Object>[] NO_FILTER = null;

    private static final String PARAM_OFFSET = "offset";
    private static final String PARAM_TASK = "task";

    private static final String PROPERTY_ERROR = "error";
    private static final String PROPERTY_ID = "id";
    private static final String PROPERTY_MESSAGES = "messages";
    private static final String PROPERTY_RESULT = "result";
    private static final String PROPERTY_STATUS = "status";

    private static final String ERROR_NOT_FOUND = "Task is not found";

    private static final String STATUS_INACTIVE = "inactive";
    private static final String STATUS_ACTIVE = "active";

    private static final String SEPARATOR_CHARS = ",;";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PROPERTY_TASKS = "tasks";

    @Reference
    private transient JobManager jobManager;

    /* ------
       Output
       ------ */

    @Override
    protected void doGet(
            final SlingHttpServletRequest request,
            final SlingHttpServletResponse response) throws IOException {

        String jobId = ServletUtil.getRequestParamString(request, PARAM_TASK);
        if (StringUtils.isBlank(jobId)) {
            outputAllTasks(request, response);
        } else if (StringUtils.containsAny(jobId, ',', ';')) {
            outputMultipleTasks(request, response, StringUtils.split(jobId, SEPARATOR_CHARS));
        } else {
            outputOneTask(request, response, jobId);
        }
    }

    private void outputAllTasks(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String userId = ServletUtil.getUserId(request);
        if (StringUtils.isEmpty(userId)) {
            ServletUtil.writeError(response, HttpStatus.SC_BAD_REQUEST, RolloutServlet.ERROR_MISSING_USER);
            LOG.warn(RolloutServlet.ERROR_MISSING_USER);
            return;
        }
        List<Job> jobs = jobManager.findJobs(JobManager.QueryType.ACTIVE, RolloutExecutor.TOPIC, NO_LIMIT, NO_FILTER)
                .stream()
                .filter(job -> userId.equals(job.getProperty(RolloutExecutor.PROPERTY_USER, String.class)))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(jobs)) {
            ServletUtil.writeError(response, HttpStatus.SC_NOT_FOUND, "There are no active tasks for the current user");
            return;
        }
        List<Map<String, Object>> tasks = jobs
            .stream()
            .map(job -> getTaskDetails(job, 0))
            .collect(Collectors.toList());
        Map<String, Object> output = Collections.singletonMap(PROPERTY_TASKS, tasks);
        ServletUtil.writeJsonResponse(response, OBJECT_MAPPER.writeValueAsString(output));
    }

    private void outputMultipleTasks(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response,
            String[] jobIds) throws IOException {
        String offsetString = ServletUtil.getRequestParamString(request, PARAM_OFFSET);
        int[] offsets = Arrays.stream(StringUtils.split(offsetString, SEPARATOR_CHARS))
            .filter(StringUtils::isNumeric)
            .mapToInt(Integer::parseInt)
            .toArray();
        List<Map<String, Object>> tasks = IntStream.range(0, jobIds.length)
            .mapToObj(index -> {
                String jobId = jobIds[index];
                int offset = index < offsets.length ? offsets[index] : 0;
                return getTaskDetails(jobId, offset);
            })
            .collect(Collectors.toList());
        Map<String, Object> output = Collections.singletonMap(PROPERTY_TASKS, tasks);
        ServletUtil.writeJsonResponse(response, OBJECT_MAPPER.writeValueAsString(output));
    }

    private void outputOneTask(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response,
            String jobId) throws IOException {

        int offset = ServletUtil.getRequestParamInt(request, PARAM_OFFSET);
        Map<String, Object> taskDetails = getTaskDetails(jobId, offset);

        if (ERROR_NOT_FOUND.equals(taskDetails.get(PROPERTY_ERROR))) {
            response.setStatus(HttpStatus.SC_NOT_FOUND);
        }

        List<Map<String, Object>> tasks = Collections.singletonList(taskDetails);
        Map<String, Object> output = Collections.singletonMap(PROPERTY_TASKS, tasks);
        ServletUtil.writeJsonResponse(response, OBJECT_MAPPER.writer().writeValueAsString(output));
    }

    /* --------------------
       Task (job) detailing
       -------------------- */

    private Map<String, Object> getTaskDetails(String jobId, int offset) {
        Job job = jobManager.getJobById(jobId);
        if (job == null) {
            Map<String, Object> output = new HashMap<>();
            output.put(PROPERTY_ID, jobId);
            output.put(PROPERTY_STATUS, STATUS_INACTIVE);
            output.put(PROPERTY_ERROR, ERROR_NOT_FOUND);
            return output;
        }
        return getTaskDetails(job, offset);
    }

    private Map<String, Object> getTaskDetails(Job job, int offset) {
        Map<String, Object> output = new HashMap<>();
        output.put(PROPERTY_ID, job.getId());
        Job.JobState jobState = job.getJobState();
        if (jobState == Job.JobState.QUEUED || jobState == Job.JobState.ACTIVE) {
            output.put(PROPERTY_STATUS, STATUS_ACTIVE);
        } else {
            output.put(PROPERTY_STATUS, STATUS_INACTIVE);
        }

        if (jobState == Job.JobState.QUEUED) {
            Map<String, Integer> queuePosition = getQueuePosition(job);
            if (queuePosition != null) {
                output.put("queue", queuePosition);
            }
        }

        if (
            (jobState == Job.JobState.ERROR
                || jobState == Job.JobState.GIVEN_UP
                || jobState == Job.JobState.DROPPED
                || jobState == Job.JobState.STOPPED)
                && StringUtils.isNotBlank(job.getResultMessage())
        ) {
            output.put(PROPERTY_ERROR, job.getResultMessage());
        } else if (
            jobState == Job.JobState.SUCCEEDED && StringUtils.isNotBlank(job.getResultMessage())
        ) {
            output.put(PROPERTY_RESULT, job.getResultMessage());
        }

        String[] log = Stream.concat(
                Stream.of(getTargetsLogEntry(job)),
                Arrays.stream(ArrayUtils.nullToEmpty(job.getProgressLog()))
            )
            .flatMap(entry -> StringUtils.contains(entry, ThrottledLogger.ENTRY_SEPARATOR)
                ? Arrays.stream(StringUtils.split(entry, ThrottledLogger.ENTRY_SEPARATOR))
                : Stream.of(entry))
            .filter(StringUtils::isNotBlank)
            .toArray(String[]::new);
        if (ArrayUtils.isEmpty(log) || offset >= log.length) {
            output.put(PROPERTY_MESSAGES, Collections.emptyList());
            return output;
        }

        List<JsonNode> processedLogMessages = IntStream.range(0, log.length)
            .skip(offset)
            .mapToObj(index -> {
                String entry = log[index];
                try {
                    JsonNode node = OBJECT_MAPPER.readTree(entry);
                    if (!(node instanceof ObjectNode)) {
                        throw new IOException("Not a JSON object");
                    }
                    ((ObjectNode) node).put(PROPERTY_ID, index);
                    return node;
                } catch (IOException e) {
                    LOG.warn("Could not parse log entry: {}", entry, e);
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        output.put(PROPERTY_MESSAGES, processedLogMessages);
        return output;
    }

    private static String getTargetsLogEntry(Job job) {
        RolloutItem[] items = RolloutPlanUtil.getItems(job.getProperty(RolloutExecutor.PROPERTY_PLAN, String.class));
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

    private Map<String, Integer> getQueuePosition(Job job) {
        Collection<Job> queuedJobs = jobManager.findJobs(JobManager.QueryType.QUEUED, RolloutExecutor.TOPIC, NO_LIMIT, NO_FILTER);
        if (CollectionUtils.isEmpty(queuedJobs)) {
            return null;
        }
        List<Job> sortedJobs = queuedJobs.stream()
            .sorted((j1, j2) -> {
                Calendar c1 = j1.getCreated();
                Calendar c2 = j2.getCreated();
                if (c1 == null && c2 == null) {
                    return 0;
                } else if (c1 == null) {
                    return 1;
                } else if (c2 == null) {
                    return -1;
                }
                return c1.compareTo(c2);
            })
            .collect(Collectors.toList());
        Map<String, Integer> result = new HashMap<>();
        result.put("position", sortedJobs.indexOf(job) + 1);
        result.put("total", sortedJobs.size());
        return result;
    }
}
