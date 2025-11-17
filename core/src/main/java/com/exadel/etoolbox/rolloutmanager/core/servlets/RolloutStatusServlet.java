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

import com.exadel.etoolbox.rolloutmanager.core.services.impl.RolloutExecutor;
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

import javax.json.Json;
import javax.json.JsonArrayBuilder;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String PARAM_OFFSET = "offset";
    private static final String PARAM_TASK = "task";

    private static final String PROPERTY_ID = "id";
    private static final String PROPERTY_MESSAGES = "messages";
    private static final String PROPERTY_STATUS = "status";

    private static final String STATUS_INACTIVE = "inactive";
    private static final String STATUS_ACTIVE = "active";

    @Reference
    private transient JobManager jobManager;

    @Override
    protected void doGet(
            final SlingHttpServletRequest request,
            final SlingHttpServletResponse response) throws IOException {

        String jobId = ServletUtil.getRequestParamString(request, PARAM_TASK);
        if (StringUtils.isBlank(jobId)) {
            outputAllTasks(request, response);
        } else if (StringUtils.containsAny(jobId, ',', ';')) {
            List<Job> jobs = Stream.of(StringUtils.split(jobId, ",;"))
                .map(id -> jobManager.getJobById(id))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            outputMultipleTasks(request, response, jobs);
        } else {
            outputOneTask(request, response, jobId);
        }
    }

    private void outputAllTasks(SlingHttpServletRequest request, SlingHttpServletResponse response) {
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
        outputMultipleTasks(request, response, jobs);
    }

    private void outputMultipleTasks(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response,
            List<Job> jobs) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        jobs
            .stream()
            .map(job -> getTaskDetails(request, job))
            .map(details -> Json.createObjectBuilder(details).build())
            .forEach(arrayBuilder::add);
        ServletUtil.writeJsonResponse(
            response,
            Json.createObjectBuilder()
                .add("tasks", arrayBuilder)
                .build()
                .toString());
    }

    private void outputOneTask(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response,
            String jobId) throws IOException {

        Job job = jobManager.getJobById(jobId);
        if (job == null) {
            Map<String, Object> output = new HashMap<>();
            output.put(PROPERTY_ID, jobId);
            output.put(PROPERTY_STATUS, STATUS_INACTIVE);
            response.setStatus(HttpStatus.SC_NOT_FOUND);
            ServletUtil.writeJsonResponse(response, OBJECT_MAPPER.writeValueAsString(output));
            return;
        }
        Map<String, Object> output = getTaskDetails(request, job);
        ServletUtil.writeJsonResponse(response, OBJECT_MAPPER.writer().writeValueAsString(output));
    }

    private Map<String, Object> getTaskDetails(SlingHttpServletRequest request, Job job) {
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
            output.put("error", job.getResultMessage());
        } else if (
            jobState == Job.JobState.SUCCEEDED && StringUtils.isNotBlank(job.getResultMessage())
        ) {
            output.put("result", job.getResultMessage());
        }

        String[] log = Stream.concat(
                Stream.of(job.getProperty(RolloutExecutor.PROPERTY_PRE_LOG, String.class)),
                Arrays.stream(ArrayUtils.nullToEmpty(job.getProgressLog()))
            )
            .flatMap(entry -> StringUtils.contains(entry, ThrottledLogger.ENTRY_SEPARATOR)
                ? Arrays.stream(StringUtils.split(entry, ThrottledLogger.ENTRY_SEPARATOR))
                : Stream.of(entry))
            .filter(StringUtils::isNotBlank)
            .toArray(String[]::new);
        int offset = ServletUtil.getRequestParamInt(request, PARAM_OFFSET);
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
