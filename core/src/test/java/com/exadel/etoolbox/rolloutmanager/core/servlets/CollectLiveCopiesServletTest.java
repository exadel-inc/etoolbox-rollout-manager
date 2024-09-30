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
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang.StringUtils;
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
import javax.json.JsonValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({AemContextExtension.class, MockitoExtension.class})
class CollectLiveCopiesServletTest {
    private static final String PATH_REQUEST_PARAM = "path";

    private static final String TEST_SOURCE_PATH = "/content/my-site/language-masters/en/testResource";
    private static final String TEST_SYNC_PATH = "/testResource";
    private static final String TEST_LIVE_COPY_PATH = "/content/my-site/fr/en";
    private static final String TEST_TARGET_PATH = TEST_LIVE_COPY_PATH + TEST_SYNC_PATH;
    private static final String TEST_NESTED_LIVE_COPY_PATH = "/content/my-site/ca/en";
    private static final String TEST_NESTED_TARGET_PATH = TEST_NESTED_LIVE_COPY_PATH + TEST_SYNC_PATH;

    private static final String EXPECTED_RESPONSE_JSON =
            "src/test/resources/com/exadel/etoolbox/rolloutmanager/core/servlets/collect-expected-items.json";

    private static final String EXPECTED_EMPTY_RESPONSE_JSON =
            "src/test/resources/com/exadel/etoolbox/rolloutmanager/core/servlets/collect-expected-items-with-no-valid-live-copy.json";

    private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

    @Mock
    private LiveRelationshipManager liveRelationshipManager;

    @Mock
    private RelationshipCheckerService relationshipCheckerService;

    @InjectMocks
    private final CollectLiveCopiesServlet fixture = new CollectLiveCopiesServlet();

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
        verifyNoInteractions(liveRelationshipManager);
        verifyNoInteractions(relationshipCheckerService);
    }

    @Test
    void doPost_EmptySourceResource_BadRequest() {
        request.addRequestParameter(PATH_REQUEST_PARAM, TEST_SOURCE_PATH);
        fixture.doPost(request, response);

        assertEquals(JsonValue.EMPTY_JSON_ARRAY.toString(), response.getOutputAsString());
        verifyNoInteractions(liveRelationshipManager);
        verifyNoInteractions(relationshipCheckerService);
    }

    @Test
    void doPost_RelationshipHasNoLiveCopy_EmptyArrayResponse() throws WCMException {
        createSourceResource();

        mockSingleLiveRelationship(TEST_SOURCE_PATH);

        fixture.doPost(request, response);
        assertEquals(JsonValue.EMPTY_JSON_ARRAY.toString(), response.getOutputAsString());
    }

    @Test
    void doPost_GetRelationshipsException_EmptyArrayResponse() throws WCMException {
        createSourceResource();

        when(liveRelationshipManager.getLiveRelationships(
                argThat((Resource resource) -> TEST_SOURCE_PATH.equals(resource.getPath())),
                any(),
                any()
                )
        ).thenThrow(new WCMException("Failed to get relationships"));

        fixture.doPost(request, response);
        assertEquals(JsonValue.EMPTY_JSON_ARRAY.toString(), response.getOutputAsString());
    }

    @Test
    void doPost_RelationshipExcludeChildren_EmptyArrayResponse() throws WCMException {
        createSourceResource();

        LiveRelationship relationship = mockSingleLiveRelationship(TEST_SOURCE_PATH);

        LiveCopy liveCopy = mock(LiveCopy.class);
        when(relationship.getLiveCopy()).thenReturn(liveCopy);
        when(relationship.getSyncPath()).thenReturn(TEST_SYNC_PATH);

        fixture.doPost(request, response);
        assertEquals(JsonValue.EMPTY_JSON_ARRAY.toString(), response.getOutputAsString());
    }

    @Test
    void doPost_NotAvailableForRollout_EmptyResponse() throws WCMException, IOException {
        createSourceResource();

        LiveRelationship relationship = mockSingleLiveRelationship(TEST_SOURCE_PATH);

        LiveCopy liveCopy = mock(LiveCopy.class);
        when(liveCopy.getPath()).thenReturn(TEST_LIVE_COPY_PATH);
        when(relationship.getLiveCopy()).thenReturn(liveCopy);
        when(relationship.getSyncPath()).thenReturn(TEST_SYNC_PATH);

        when(liveCopy.isDeep()).thenReturn(true);
        when(relationshipCheckerService.isAvailableForSync(any(), any(), any(), any()))
                .thenReturn(false);

        fixture.doPost(request, response);

        String expected = new String(Files.readAllBytes(Paths.get(EXPECTED_EMPTY_RESPONSE_JSON)))
                .replaceAll("(\\r|\\n|\\t|\\s)", StringUtils.EMPTY);
        assertEquals(expected, response.getOutputAsString());
    }

    @Test
    void doPost_CollectItems_NonEmptyArrayResponse() throws WCMException, IOException {
        createSourceResource();
        context.create().resource(TEST_LIVE_COPY_PATH);
        context.create().resource(TEST_TARGET_PATH);
        context.create().resource(TEST_NESTED_LIVE_COPY_PATH);

        mockRelationshipWithTarget(TEST_SOURCE_PATH, TEST_TARGET_PATH, TEST_LIVE_COPY_PATH);
        mockRelationshipWithTarget(TEST_LIVE_COPY_PATH, TEST_NESTED_TARGET_PATH, TEST_NESTED_LIVE_COPY_PATH);

        doReturn(mock(RangeIterator.class)).when(liveRelationshipManager).getLiveRelationships(
                argThat((Resource resource) -> TEST_NESTED_LIVE_COPY_PATH.equals(resource.getPath())),
                any(),
                any()
        );

        when(relationshipCheckerService.isAvailableForSync(any(), any(), any(), any()))
                .thenReturn(true);

        fixture.doPost(request, response);

        String expected = new String(Files.readAllBytes(Paths.get(EXPECTED_RESPONSE_JSON)))
                .replaceAll("(\\r|\\n|\\t|\\s)", StringUtils.EMPTY);
        assertEquals(expected, response.getOutputAsString());
    }

    private LiveRelationship mockSingleLiveRelationship(String sourcePath) throws WCMException {
        LiveRelationship relationship = mock(LiveRelationship.class);
        RangeIterator relationships = mock(RangeIterator.class);
        when(relationships.hasNext()).thenReturn(true, false);
        when(relationships.next()).thenReturn(relationship);
        doReturn(relationships).when(liveRelationshipManager).getLiveRelationships(
                argThat((Resource resource) -> sourcePath.equals(resource.getPath())),
                any(),
                any()
        );
        return relationship;
    }

    private void mockRelationshipWithTarget(String sourcePath, String target, String liveCopyPath) throws WCMException {
        LiveRelationship relationship = mockSingleLiveRelationship(sourcePath);
        LiveCopy liveCopy = mock(LiveCopy.class);
        when(relationship.getLiveCopy()).thenReturn(liveCopy);
        when(relationship.getSyncPath()).thenReturn(TEST_SYNC_PATH);
        when(relationship.getTargetPath()).thenReturn(target);
        when(liveCopy.isDeep()).thenReturn(true);
        when(liveCopy.getPath()).thenReturn(liveCopyPath);
    }

    private void createSourceResource() {
        context.create().resource(TEST_SOURCE_PATH);
        request.addRequestParameter(PATH_REQUEST_PARAM, TEST_SOURCE_PATH);
    }
}