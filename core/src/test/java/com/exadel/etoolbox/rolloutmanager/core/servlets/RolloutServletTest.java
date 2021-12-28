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
import com.day.cq.wcm.msm.api.RolloutManager;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.json.Json;
import javax.json.JsonValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({AemContextExtension.class, MockitoExtension.class})
class RolloutServletTest {
    private static final String SELECTION_JSON_ARRAY_PARAM = "selectionJsonArray";
    private static final String FAILED_TARGETS_RESPONSE_PARAM = "failedTargets";

    private static final String SELECTED_LIVECOPIES_REQUEST_JSON =
            "src/test/resources/com/exadel/etoolbox/rolloutmanager/core/servlets/rollout-selected-items.json";
    private static final String SELECTED_LIVECOPIES_EMPTY_TARGETS_REQUEST_JSON =
            "src/test/resources/com/exadel/etoolbox/rolloutmanager/core/servlets/rollout-selected-items-empty-targets.json";
    private static final String TEST_PAGES_STRUCTURE_PATH =
            "/com/exadel/etoolbox/rolloutmanager/core/servlets/rollout-pages-structure.json";
    private static final String TEST_FOLDER_PATH = "/content/we-retail";

    private static final String NOT_A_JSON_STRING = "not-a-json";

    private static final List<String> EXPECTED_FAILED_PATH = Arrays.asList(
            "/content/we-retail/ca/en/experience",
            "/content/we-retail/us/en/experience",
            "/content/we-retail/es/ca-es-livecopy/experience",
            "/content/we-retail/fr/ca-fr-livecopy/experience",
            "/content/we-retail/de/ca-es-de-livecopy/experience",
            "/content/we-retail/ch/ca-es-ch-livecopy/experience"
    );

    private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

    @Mock
    private RolloutManager rolloutManager;

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
    void doPost_EmptyParams_BadRequest() {
        fixture.doPost(request, response);

        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        verifyNoInteractions(rolloutManager);
    }

    @Test
    void doPost_EmptyRolloutItems_BadRequest() {
        request.addRequestParameter(SELECTION_JSON_ARRAY_PARAM, JsonValue.EMPTY_JSON_ARRAY.toString());
        fixture.doPost(request, response);

        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        verifyNoInteractions(rolloutManager);
    }

    @Test
    void doPost_EmptyPageManager_BadRequest() throws IOException {
        SlingHttpServletRequest requestMock = mock(SlingHttpServletRequest.class);
        ResourceResolver resourceResolverMock = mock(ResourceResolver.class);
        when(requestMock.getResourceResolver()).thenReturn(resourceResolverMock);

        String selectedLiveCopies = new String(Files.readAllBytes(Paths.get(SELECTED_LIVECOPIES_REQUEST_JSON)));
        RequestParameter requestParameter = mock(RequestParameter.class);
        when(requestMock.getRequestParameter(SELECTION_JSON_ARRAY_PARAM)).thenReturn(requestParameter);
        when(requestParameter.getString()).thenReturn(selectedLiveCopies);

        fixture.doPost(requestMock, response);

        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        verifyNoInteractions(rolloutManager);
    }

    @Test
    void doPost_RolloutTargets_Success() throws IOException, WCMException {
        context.load().json(TEST_PAGES_STRUCTURE_PATH, TEST_FOLDER_PATH);

        String selectedLiveCopies = new String(Files.readAllBytes(Paths.get(SELECTED_LIVECOPIES_REQUEST_JSON)));
        request.addRequestParameter(SELECTION_JSON_ARRAY_PARAM, selectedLiveCopies);

        fixture.doPost(request, response);

        verify(rolloutManager, times(6)).rollout(any(RolloutManager.RolloutParams.class));

        assertEquals(HttpStatus.SC_OK, response.getStatus());
    }

    @Test
    void doPost_RolloutException_FailedTargetsInResponse() throws IOException, WCMException {
        context.load().json(TEST_PAGES_STRUCTURE_PATH, TEST_FOLDER_PATH);

        String selectedLiveCopies = new String(Files.readAllBytes(Paths.get(SELECTED_LIVECOPIES_REQUEST_JSON)));
        request.addRequestParameter(SELECTION_JSON_ARRAY_PARAM, selectedLiveCopies);

        doThrow(new WCMException("Failed to rollout")).when(rolloutManager)
                .rollout(any(RolloutManager.RolloutParams.class));

        fixture.doPost(request, response);

        String expectedResponse = Json.createObjectBuilder()
                .add(FAILED_TARGETS_RESPONSE_PARAM, Json.createArrayBuilder(EXPECTED_FAILED_PATH))
                .build()
                .toString();

        assertEquals(expectedResponse, response.getOutputAsString());

        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
    }

    @Test
    void doPost_EmptyTargets_NoRollout() throws IOException, WCMException {
        String selectedLiveCopies =
                new String(Files.readAllBytes(Paths.get(SELECTED_LIVECOPIES_EMPTY_TARGETS_REQUEST_JSON)));
        request.addRequestParameter(SELECTION_JSON_ARRAY_PARAM, selectedLiveCopies);

        fixture.doPost(request, response);

        verifyNoInteractions(rolloutManager);
    }

    @Test
    void doPost_ParseJsonException_NoRollout() throws IOException, WCMException {
        request.addRequestParameter(SELECTION_JSON_ARRAY_PARAM, NOT_A_JSON_STRING);

        fixture.doPost(request, response);

        verifyNoInteractions(rolloutManager);
    }

    @Test
    void doPost_NonExistingMaster_FailedTargetsInResponse() throws IOException {
        String selectedLiveCopies = new String(Files.readAllBytes(Paths.get(SELECTED_LIVECOPIES_REQUEST_JSON)));
        request.addRequestParameter(SELECTION_JSON_ARRAY_PARAM, selectedLiveCopies);

        fixture.doPost(request, response);

        String expectedResponse = Json.createObjectBuilder()
                .add(FAILED_TARGETS_RESPONSE_PARAM, Json.createArrayBuilder(EXPECTED_FAILED_PATH))
                .build()
                .toString();

        assertEquals(expectedResponse, response.getOutputAsString());

        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
    }
}