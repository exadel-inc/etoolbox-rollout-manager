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
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({AemContextExtension.class, MockitoExtension.class})
class RolloutServletTest {

    private static final String PARAM_SELECTION_JSON_ARRAY = "selectionJsonArray";
    private static final String PARAM_IS_DEEP = "isDeepRollout";
    private static final String PARAM_SHOULD_ACTIVATE = "shouldActivate";

    private static final String VALID_PLAN_JSON_PATH =
            "src/test/resources/com/exadel/etoolbox/rolloutmanager/core/servlets/rollout-selected-items.json";

    private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

    @Mock
    private JobManager jobManager;

    @InjectMocks
    private final RolloutServlet fixture = new RolloutServlet();

    private MockSlingHttpServletRequest request;
    private MockSlingHttpServletResponse response;

    @BeforeEach
    void setup() {
        request = context.request();
        response = context.response();
    }

    @Test
    void doPost_MissingPlan_ReturnsBadRequest() {
        fixture.doPost(request, response);

        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        String output = response.getOutputAsString();
        assertTrue(output.contains("error"));
        assertTrue(output.contains("Rollout plan is missing or invalid"));
        assertEquals("true", response.getHeader("x-aem-error-pass"));
    }

    @Test
    void doPost_EmptyPlanItems_ReturnsBadRequest() {
        request.addRequestParameter(PARAM_SELECTION_JSON_ARRAY, "[]");

        fixture.doPost(request, response);

        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        String output = response.getOutputAsString();
        assertTrue(output.contains("Rollout plan is missing or invalid"));
    }

    @Test
    void doPost_InvalidJson_ReturnsBadRequest() {
        request.addRequestParameter(PARAM_SELECTION_JSON_ARRAY, "not-a-json");

        fixture.doPost(request, response);

        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        String output = response.getOutputAsString();
        assertTrue(output.contains("Rollout plan is missing or invalid"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void doPost_ValidPlanWithUser_CreatesJobAndReturns201() throws IOException {
        String plan = new String(Files.readAllBytes(Paths.get(VALID_PLAN_JSON_PATH)));
        request.addRequestParameter(PARAM_SELECTION_JSON_ARRAY, plan);
        request.addRequestParameter(PARAM_IS_DEEP, "true");
        request.addRequestParameter(PARAM_SHOULD_ACTIVATE, "true");

        Job job = org.mockito.Mockito.mock(Job.class);
        when(job.getId()).thenReturn("job-123");
        when(jobManager.addJob(eq(RolloutExecutor.TOPIC), any(Map.class))).thenReturn(job);

        fixture.doPost(request, response);

        assertEquals(HttpStatus.SC_CREATED, response.getStatus());
        String output = response.getOutputAsString();
        assertTrue(output.contains("task"));
        assertTrue(output.contains("job-123"));

        ArgumentCaptor<Map<String, Object>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jobManager).addJob(eq(RolloutExecutor.TOPIC), propsCaptor.capture());
        Map<String, Object> props = propsCaptor.getValue();

        assertNotNull(props);
        assertEquals(plan, props.get(RolloutExecutor.PROPERTY_PLAN));
        assertEquals(Boolean.TRUE, props.get(RolloutExecutor.PROPERTY_DEEP));
        assertEquals(Boolean.TRUE, props.get(RolloutExecutor.PROPERTY_ACTIVATE));
    }
}