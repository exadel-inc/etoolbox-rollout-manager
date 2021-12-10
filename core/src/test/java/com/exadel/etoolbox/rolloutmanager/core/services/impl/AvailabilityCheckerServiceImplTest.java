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

package com.exadel.etoolbox.rolloutmanager.core.services.impl;

import com.day.cq.wcm.msm.api.LiveCopy;
import com.day.cq.wcm.msm.api.LiveRelationship;
import com.google.common.collect.ImmutableSet;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class AvailabilityCheckerServiceImplTest {
    private static final String TEST_SYNC_PATH = "/page-to-sync";
    private static final String TEST_PARENT_SYNC_PATH = "/parent-page/page-to-sync";
    private static final String TEST_TARGET_PATH = "/content/my-site/en";
    private static final String TEST_CURRENT_PAGE_EXCLUSION = "page-to-sync";
    private static final String TEST_OTHER_PAGE_EXCLUSION = "other-page";
    private static final String TEST_PARENT_PAGE_EXCLUSION = "parent-page";
    private static final String TEST_CONTENT_PATH = "/content";

    private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

    private final AvailabilityCheckerServiceImpl fixture = new AvailabilityCheckerServiceImpl();

    @Test
    void isAvailableForRollout_NullLiveCopy_False() {
        LiveRelationship relationshipMock = mock(LiveRelationship.class);

        assertFalse(fixture.isAvailableForRollout(relationshipMock, context.resourceResolver()));
    }

    @Test
    void isAvailableForRollout_EmptySyncPath_True() {
        LiveRelationship relationshipMock = mock(LiveRelationship.class);
        when(relationshipMock.getLiveCopy()).thenReturn(mock(LiveCopy.class));
        when(relationshipMock.getSyncPath()).thenReturn(StringUtils.EMPTY);

        assertTrue(fixture.isAvailableForRollout(relationshipMock, context.resourceResolver()));
    }

    @Test
    void isAvailableForRollout_InExclusions_False() {
        LiveRelationship relationshipMock = mock(LiveRelationship.class);
        LiveCopy LiveCopyMock = mock(LiveCopy.class);
        when(relationshipMock.getLiveCopy()).thenReturn(LiveCopyMock);

        Set<String> exclusions = ImmutableSet.of(TEST_CURRENT_PAGE_EXCLUSION);
        when(LiveCopyMock.getExclusions()).thenReturn(exclusions);

        when(relationshipMock.getSyncPath()).thenReturn(TEST_SYNC_PATH);

        assertFalse(fixture.isAvailableForRollout(relationshipMock, context.resourceResolver()));
    }

    @Test
    void isAvailableForRollout_ParentInExclusions_False() {
        LiveRelationship relationshipMock = mock(LiveRelationship.class);
        LiveCopy LiveCopyMock = mock(LiveCopy.class);
        when(relationshipMock.getLiveCopy()).thenReturn(LiveCopyMock);

        Set<String> exclusions = ImmutableSet.of(TEST_PARENT_PAGE_EXCLUSION);
        when(LiveCopyMock.getExclusions()).thenReturn(exclusions);

        when(relationshipMock.getSyncPath()).thenReturn(TEST_PARENT_SYNC_PATH);

        assertFalse(fixture.isAvailableForRollout(relationshipMock, context.resourceResolver()));
    }

    @Test
    void isAvailableForRollout_TargetParentExists_True() {
        LiveRelationship relationshipMock = mock(LiveRelationship.class);
        LiveCopy LiveCopyMock = mock(LiveCopy.class);
        when(relationshipMock.getLiveCopy()).thenReturn(LiveCopyMock);

        Set<String> exclusions = ImmutableSet.of(TEST_OTHER_PAGE_EXCLUSION);
        when(LiveCopyMock.getExclusions()).thenReturn(exclusions);

        when(relationshipMock.getSyncPath()).thenReturn(TEST_SYNC_PATH);

        when(relationshipMock.getTargetPath()).thenReturn(TEST_TARGET_PATH + TEST_SYNC_PATH);
        context.create().resource(TEST_TARGET_PATH);

        assertTrue(fixture.isAvailableForRollout(relationshipMock, context.resourceResolver()));
    }

    @Test
    void isAvailableForRollout_TargetParentEmptyPath_True() {
        LiveRelationship relationshipMock = mock(LiveRelationship.class);
        LiveCopy LiveCopyMock = mock(LiveCopy.class);
        when(relationshipMock.getLiveCopy()).thenReturn(LiveCopyMock);

        Set<String> exclusions = ImmutableSet.of(TEST_OTHER_PAGE_EXCLUSION);
        when(LiveCopyMock.getExclusions()).thenReturn(exclusions);

        when(relationshipMock.getSyncPath()).thenReturn(TEST_SYNC_PATH);

        when(relationshipMock.getTargetPath()).thenReturn(TEST_CONTENT_PATH);

        assertTrue(fixture.isAvailableForRollout(relationshipMock, context.resourceResolver()));
    }
}