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
import com.exadel.etoolbox.rolloutmanager.core.services.AvailabilityCheckerService;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.jcr.RangeIterator;
import javax.json.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({AemContextExtension.class, MockitoExtension.class})
class BlueprintCheckServletTest {
    private static final String PATH_PARAM = "path";
    private static final String IS_AVAILABLE_FOR_ROLLOUT_PARAM = "isAvailableForRollout";

    private static final String TEST_RESOURCE_PATH = "/content/my-site/en/testResource";

    private final AemContext context = new AemContext(ResourceResolverType.RESOURCERESOLVER_MOCK);

    @Mock
    private LiveRelationshipManager liveRelationshipManager;

    @Mock
    private AvailabilityCheckerService availabilityCheckerService;

    @InjectMocks
    private final BlueprintCheckServlet fixture = new BlueprintCheckServlet();

    private MockSlingHttpServletRequest request;
    private MockSlingHttpServletResponse response;

    @BeforeEach
    void setup() {
        request = context.request();
        response = context.response();
    }

    @Test
    void doPost_AvailableForRollout_True() throws WCMException {
        context.create().resource(TEST_RESOURCE_PATH);
        request.addRequestParameter(PATH_PARAM, TEST_RESOURCE_PATH);

        RangeIterator relationships = mock(RangeIterator.class);
        when(relationships.hasNext()).thenReturn(true);
        LiveRelationship liveRelationship = mock(LiveRelationship.class);
        when(relationships.next()).thenReturn(liveRelationship);
        when(liveRelationshipManager.getLiveRelationships(any(Resource.class), any(), any()))
                .thenReturn(relationships);
        when(availabilityCheckerService.isAvailableForRollout(liveRelationship, request.getResourceResolver()))
                .thenReturn(true);

        fixture.doPost(request, response);
        assertJsonResponse(true);
    }

    @Test
    void doPost_AvailableForRollout_False() throws WCMException {
        context.create().resource(TEST_RESOURCE_PATH);
        request.addRequestParameter(PATH_PARAM, TEST_RESOURCE_PATH);

        RangeIterator relationships = mock(RangeIterator.class);
        when(relationships.hasNext()).thenReturn(true, false);
        LiveRelationship liveRelationship = mock(LiveRelationship.class);
        when(relationships.next()).thenReturn(liveRelationship);
        when(liveRelationshipManager.getLiveRelationships(any(Resource.class), any(), any()))
                .thenReturn(relationships);
        when(availabilityCheckerService.isAvailableForRollout(liveRelationship, request.getResourceResolver()))
                .thenReturn(false);

        fixture.doPost(request, response);
        assertJsonResponse(false);
    }

    @Test
    void doPost_NoLiveRelationships_False() throws WCMException {
        context.create().resource(TEST_RESOURCE_PATH);
        request.addRequestParameter(PATH_PARAM, TEST_RESOURCE_PATH);

        RangeIterator relationships = mock(RangeIterator.class);
        when(relationships.hasNext()).thenReturn(false);
        when(liveRelationshipManager.getLiveRelationships(any(Resource.class), any(), any()))
                .thenReturn(relationships);

        fixture.doPost(request, response);
        assertJsonResponse(false);
    }

    @Test
    void doPost_NoLiveRelationshipsException_False() throws WCMException {
        context.create().resource(TEST_RESOURCE_PATH);
        request.addRequestParameter(PATH_PARAM, TEST_RESOURCE_PATH);

        when(liveRelationshipManager.getLiveRelationships(any(Resource.class), any(), any()))
                .thenThrow(new WCMException("Failed to getLiveRelationships"));

        fixture.doPost(request, response);
        assertJsonResponse(false);
    }

    @Test
    void doPost_EmptyParams_BadRequest() {
        fixture.doPost(request, response);

        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        verifyNoInteractions(liveRelationshipManager);
    }

    @Test
    void doPost_ResourceAbsent_BadRequest() {
        request.addRequestParameter(PATH_PARAM, TEST_RESOURCE_PATH);

        fixture.doPost(request, response);

        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
        verifyNoInteractions(liveRelationshipManager);
    }

    private void assertJsonResponse(boolean expectedValue) {
        String expectedJsonResponse = Json.createObjectBuilder()
                .add(IS_AVAILABLE_FOR_ROLLOUT_PARAM, expectedValue)
                .build()
                .toString();
        String jsonResponse = response.getOutputAsString();

        assertEquals(expectedJsonResponse, jsonResponse);
    }
}