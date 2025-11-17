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
import com.exadel.etoolbox.rolloutmanager.core.utils.ThrottledLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({AemContextExtension.class, MockitoExtension.class})
class RolloutStatusServletTest {

    private static final String PARAM_TASK = "task";
    private static final String PARAM_OFFSET = "offset";

    private static final String TEST_JOB_ID = "test-job-123";
    private static final String TEST_USER_ID = "admin";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

    @Mock
    private JobManager jobManager;

    @InjectMocks
    private final RolloutStatusServlet fixture = new RolloutStatusServlet();

    @Test
    void shouldReturn404WhenTasksAreMissing() throws IOException {
        fixture.doGet(context.request(), context.response());

        assertEquals(HttpStatus.SC_NOT_FOUND, context.response().getStatus());
        String output = context.response().getOutputAsString();
        assertTrue(output.contains("error"));
    }

    @Test
    void shouldReturn404WhenNoActiveTasksExist() throws IOException {
        when(jobManager.findJobs(
            eq(JobManager.QueryType.ACTIVE),
            eq(RolloutExecutor.TOPIC),
            anyLong(),
            any()))
            .thenReturn(Collections.emptyList());

        fixture.doGet(context.request(), context.response());

        assertEquals(HttpStatus.SC_NOT_FOUND, context.response().getStatus());
        String output = context.response().getOutputAsString();
        assertTrue(output.contains("error"));
        assertTrue(output.contains("There are no active tasks for the current user"));
    }

    @Test
    void shouldReturnActiveTasksForCurrentUser() throws IOException {
        Job job1 = mock(Job.class);
        when(job1.getProperty(RolloutExecutor.PROPERTY_USER, String.class)).thenReturn(TEST_USER_ID);
        when(job1.getId()).thenReturn("job-1");
        when(job1.getJobState()).thenReturn(Job.JobState.ACTIVE);
        when(job1.getProgressLog()).thenReturn(null);

        Job job2 = mock(Job.class);
        when(job2.getProperty(RolloutExecutor.PROPERTY_USER, String.class)).thenReturn(TEST_USER_ID);
        when(job2.getId()).thenReturn("job-2");
        when(job2.getJobState()).thenReturn(Job.JobState.QUEUED);
        when(job2.getProgressLog()).thenReturn(null);

        Job job3 = mock(Job.class);
        when(job3.getProperty(RolloutExecutor.PROPERTY_USER, String.class)).thenReturn("other-user");

        Collection<Job> jobs = Arrays.asList(job1, job2, job3);
        when(jobManager.findJobs(
            eq(JobManager.QueryType.ACTIVE),
            eq(RolloutExecutor.TOPIC),
            anyLong(),
            any()))
            .thenReturn(jobs);

        fixture.doGet(context.request(), context.response());

        assertEquals(HttpStatus.SC_OK, context.response().getStatus());
        String output = context.response().getOutputAsString();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(output);
        assertNotNull(jsonNode.get("tasks"));
        assertEquals(2, jsonNode.get("tasks").size());

        JsonNode task1 = jsonNode.get("tasks").get(0);
        assertEquals("job-1", task1.get("id").asText());
        assertEquals("active", task1.get("status").asText());
        assertNotNull(task1.get("messages"));

        JsonNode task2 = jsonNode.get("tasks").get(1);
        assertEquals("job-2", task2.get("id").asText());
        assertEquals("active", task2.get("status").asText());
        assertNotNull(task2.get("messages"));
    }

    @Test
    void shouldReturnDetailsForMultipleTasks() throws IOException {
        for (String tasks : Arrays.asList("job-1,job-2,job-3", "job-1;job-2")) {
            context.request().addRequestParameter(PARAM_TASK, tasks);

            Job job1 = mock(Job.class);
            when(job1.getId()).thenReturn("job-1");
            when(job1.getJobState()).thenReturn(Job.JobState.ACTIVE);
            when(job1.getProgressLog()).thenReturn(null);

            Job job2 = mock(Job.class);
            when(job2.getId()).thenReturn("job-2");
            when(job2.getJobState()).thenReturn(Job.JobState.SUCCEEDED);
            when(job2.getProgressLog()).thenReturn(null);

            when(jobManager.getJobById("job-1")).thenReturn(job1);
            when(jobManager.getJobById("job-2")).thenReturn(job2);

            fixture.doGet(context.request(), context.response());

            assertEquals(HttpStatus.SC_OK, context.response().getStatus());
            String output = context.response().getOutputAsString();
            JsonNode jsonNode = OBJECT_MAPPER.readTree(output);
            assertNotNull(jsonNode.get("tasks"));
            assertEquals(2, jsonNode.get("tasks").size());

            JsonNode task1 = jsonNode.get("tasks").get(0);
            assertEquals("job-1", task1.get("id").asText());
            assertEquals("active", task1.get("status").asText());

            JsonNode task2 = jsonNode.get("tasks").get(1);
            assertEquals("job-2", task2.get("id").asText());
            assertEquals("inactive", task2.get("status").asText());

            context.response().reset();
        }
    }

    @Test
    void shouldReturnActiveStatusForQueuedJob() throws IOException {
        context.request().addRequestParameter(PARAM_TASK, TEST_JOB_ID);

        Job job = mock(Job.class);
        when(job.getId()).thenReturn(TEST_JOB_ID);
        when(job.getJobState()).thenReturn(Job.JobState.QUEUED);
        when(job.getProgressLog()).thenReturn(null);
        when(jobManager.getJobById(TEST_JOB_ID)).thenReturn(job);

        fixture.doGet(context.request(), context.response());

        assertEquals(HttpStatus.SC_OK, context.response().getStatus());
        String output = context.response().getOutputAsString();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(output);
        assertEquals(TEST_JOB_ID, jsonNode.get("id").asText());
        assertEquals("active", jsonNode.get("status").asText());
        assertTrue(jsonNode.get("messages").isEmpty());
    }

    @Test
    void shouldReturnActiveStatusForRunningJob() throws IOException {
        context.request().addRequestParameter(PARAM_TASK, TEST_JOB_ID);

        Job job = mock(Job.class);
        when(job.getId()).thenReturn(TEST_JOB_ID);
        when(job.getJobState()).thenReturn(Job.JobState.ACTIVE);
        when(job.getProgressLog()).thenReturn(null);
        when(jobManager.getJobById(TEST_JOB_ID)).thenReturn(job);

        fixture.doGet(context.request(), context.response());

        assertEquals(HttpStatus.SC_OK, context.response().getStatus());
        String output = context.response().getOutputAsString();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(output);
        assertEquals("active", jsonNode.get("status").asText());
    }

    @Test
    void shouldReturnInactiveStatusForCompletedJob() throws IOException {
        context.request().addRequestParameter(PARAM_TASK, TEST_JOB_ID);

        Job job = mock(Job.class);
        when(job.getId()).thenReturn(TEST_JOB_ID);
        when(job.getJobState()).thenReturn(Job.JobState.SUCCEEDED);
        when(job.getProgressLog()).thenReturn(null);
        when(jobManager.getJobById(TEST_JOB_ID)).thenReturn(job);

        fixture.doGet(context.request(), context.response());

        assertEquals(HttpStatus.SC_OK, context.response().getStatus());
        String output = context.response().getOutputAsString();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(output);
        assertEquals("inactive", jsonNode.get("status").asText());
    }

    @Test
    void shouldReturnInactiveStatusWhenJobNotFound() throws IOException {
        context.request().addRequestParameter(PARAM_TASK, TEST_JOB_ID);
        when(jobManager.getJobById(TEST_JOB_ID)).thenReturn(null);

        fixture.doGet(context.request(), context.response());

        assertEquals(HttpStatus.SC_NOT_FOUND, context.response().getStatus());
        String output = context.response().getOutputAsString();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(output);
        assertEquals(TEST_JOB_ID, jsonNode.get("id").asText());
        assertEquals("inactive", jsonNode.get("status").asText());
    }

    @Test
    void shouldIncludeResultMessageForSucceededJob() throws IOException {
        context.request().addRequestParameter(PARAM_TASK, TEST_JOB_ID);

        Job job = mock(Job.class);
        when(job.getId()).thenReturn(TEST_JOB_ID);
        when(job.getJobState()).thenReturn(Job.JobState.SUCCEEDED);
        when(job.getResultMessage()).thenReturn("Job completed successfully");
        when(job.getProgressLog()).thenReturn(null);
        when(jobManager.getJobById(TEST_JOB_ID)).thenReturn(job);

        fixture.doGet(context.request(), context.response());

        String output = context.response().getOutputAsString();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(output);
        assertEquals("Job completed successfully", jsonNode.get("result").asText());
    }

    @Test
    void shouldIncludeErrorMessageForFailedJob() throws IOException {
        context.request().addRequestParameter(PARAM_TASK, TEST_JOB_ID);

        Job job = mock(Job.class);
        when(job.getId()).thenReturn(TEST_JOB_ID);
        when(job.getJobState()).thenReturn(Job.JobState.ERROR);
        when(job.getResultMessage()).thenReturn("Something went wrong");
        when(job.getProgressLog()).thenReturn(null);
        when(jobManager.getJobById(TEST_JOB_ID)).thenReturn(job);

        fixture.doGet(context.request(), context.response());

        assertEquals(HttpStatus.SC_OK, context.response().getStatus());
        String output = context.response().getOutputAsString();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(output);
        assertEquals("inactive", jsonNode.get("status").asText());
        assertEquals("Something went wrong", jsonNode.get("error").asText());
    }

    @Test
    void shouldIncludeErrorMessageForGivenUpJob() throws IOException {
        context.request().addRequestParameter(PARAM_TASK, TEST_JOB_ID);

        Job job = mock(Job.class);
        when(job.getId()).thenReturn(TEST_JOB_ID);
        when(job.getJobState()).thenReturn(Job.JobState.GIVEN_UP);
        when(job.getResultMessage()).thenReturn("Job gave up");
        when(job.getProgressLog()).thenReturn(null);
        when(jobManager.getJobById(TEST_JOB_ID)).thenReturn(job);

        fixture.doGet(context.request(), context.response());

        String output = context.response().getOutputAsString();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(output);
        assertEquals("Job gave up", jsonNode.get("error").asText());
    }

    @Test
    void shouldParseProgressLogMessages() throws IOException {
        context.request().addRequestParameter(PARAM_TASK, TEST_JOB_ID);

        String logMessage1 = "{\"message\":\"Processing page 1\",\"level\":\"info\"}";
        String logMessage2 = "{\"message\":\"Processing page 2\",\"level\":\"info\"}";

        Job job = mock(Job.class);
        when(job.getId()).thenReturn(TEST_JOB_ID);
        when(job.getJobState()).thenReturn(Job.JobState.ACTIVE);
        when(job.getProgressLog()).thenReturn(new String[]{logMessage1, logMessage2});
        when(jobManager.getJobById(TEST_JOB_ID)).thenReturn(job);

        fixture.doGet(context.request(), context.response());

        String output = context.response().getOutputAsString();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(output);
        JsonNode messages = jsonNode.get("messages");
        assertEquals(2, messages.size());
        assertEquals("Processing page 1", messages.get(0).get("message").asText());
        assertEquals("Processing page 2", messages.get(1).get("message").asText());
        assertEquals(0, messages.get(0).get("id").asInt());
        assertEquals(1, messages.get(1).get("id").asInt());
    }

    @Test
    void shouldHandleProgressLogWithThrottledLoggerSeparator() throws IOException {
        context.request().addRequestParameter(PARAM_TASK, TEST_JOB_ID);

        String logMessage1 = "{\"message\":\"Message 1\"}";
        String logMessage2 = "{\"message\":\"Message 2\"}";
        String logMessage3 = "{\"message\":\"Message 3\"}";
        String combinedEntry = logMessage1 + ThrottledLogger.ENTRY_SEPARATOR + logMessage2;

        Job job = mock(Job.class);
        when(job.getId()).thenReturn(TEST_JOB_ID);
        when(job.getJobState()).thenReturn(Job.JobState.ACTIVE);
        when(job.getProgressLog()).thenReturn(new String[]{combinedEntry, logMessage3});
        when(jobManager.getJobById(TEST_JOB_ID)).thenReturn(job);

        fixture.doGet(context.request(), context.response());

        String output = context.response().getOutputAsString();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(output);
        JsonNode messages = jsonNode.get("messages");
        assertEquals(3, messages.size());
        assertEquals("Message 1", messages.get(0).get("message").asText());
        assertEquals("Message 2", messages.get(1).get("message").asText());
        assertEquals("Message 3", messages.get(2).get("message").asText());
    }

    @Test
    void shouldApplyOffsetToLogMessages() throws IOException {
        context.request().addRequestParameter(PARAM_TASK, TEST_JOB_ID);
        context.request().addRequestParameter(PARAM_OFFSET, "2");

        String logMessage1 = "{\"message\":\"Message 1\"}";
        String logMessage2 = "{\"message\":\"Message 2\"}";
        String logMessage3 = "{\"message\":\"Message 3\"}";

        Job job = mock(Job.class);
        when(job.getId()).thenReturn(TEST_JOB_ID);
        when(job.getJobState()).thenReturn(Job.JobState.ACTIVE);
        when(job.getProgressLog()).thenReturn(new String[]{logMessage1, logMessage2, logMessage3});
        when(jobManager.getJobById(TEST_JOB_ID)).thenReturn(job);

        fixture.doGet(context.request(), context.response());

        String output = context.response().getOutputAsString();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(output);
        JsonNode messages = jsonNode.get("messages");
        assertEquals(1, messages.size());
        assertEquals("Message 3", messages.get(0).get("message").asText());
        assertEquals(2, messages.get(0).get("id").asInt());
    }

    @Test
    void shouldReturnEmptyMessagesWhenOffsetExceedsLogLength() throws IOException {
        context.request().addRequestParameter(PARAM_TASK, TEST_JOB_ID);
        context.request().addRequestParameter(PARAM_OFFSET, "10");

        String logMessage = "{\"message\":\"Message 1\"}";

        Job job = mock(Job.class);
        when(job.getId()).thenReturn(TEST_JOB_ID);
        when(job.getJobState()).thenReturn(Job.JobState.ACTIVE);
        when(job.getProgressLog()).thenReturn(new String[]{logMessage});
        when(jobManager.getJobById(TEST_JOB_ID)).thenReturn(job);

        fixture.doGet(context.request(), context.response());

        String output = context.response().getOutputAsString();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(output);
        JsonNode messages = jsonNode.get("messages");
        assertTrue(messages.isEmpty());
    }

    @Test
    void shouldSkipInvalidJsonLogEntries() throws IOException {
        context.request().addRequestParameter(PARAM_TASK, TEST_JOB_ID);

        Job job = mock(Job.class);
        when(job.getId()).thenReturn(TEST_JOB_ID);
        when(job.getJobState()).thenReturn(Job.JobState.ACTIVE);
        when(job.getProgressLog()).thenReturn(new String[]{
            "not a json",
            "{\"message\":\"Valid message\"}",
            "\"just a string\"",
            StringUtils.EMPTY
        });
        when(jobManager.getJobById(TEST_JOB_ID)).thenReturn(job);

        fixture.doGet(context.request(), context.response());

        String output = context.response().getOutputAsString();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(output);
        JsonNode messages = jsonNode.get("messages");
        assertEquals(1, messages.size());
        assertEquals("Valid message", messages.get(0).get("message").asText());
        assertEquals(1, messages.get(0).get("id").asInt());
    }

    @Test
    void shouldHandleEmptyProgressLog() throws IOException {
        context.request().addRequestParameter(PARAM_TASK, TEST_JOB_ID);

        Job job = mock(Job.class);
        when(job.getId()).thenReturn(TEST_JOB_ID);
        when(job.getJobState()).thenReturn(Job.JobState.ACTIVE);
        when(job.getProgressLog()).thenReturn(new String[]{});
        when(jobManager.getJobById(TEST_JOB_ID)).thenReturn(job);

        fixture.doGet(context.request(), context.response());

        String output = context.response().getOutputAsString();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(output);
        JsonNode messages = jsonNode.get("messages");
        assertTrue(messages.isEmpty());
    }
}

