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
import com.exadel.etoolbox.rolloutmanager.core.services.RelationshipCheckerService;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@Component(service = RelationshipCheckerService.class)
public class RelationshipCheckerServiceImpl implements RelationshipCheckerService {

    private static final Logger LOG = LoggerFactory.getLogger(RelationshipCheckerServiceImpl.class);
    private static final String SLASH = "/";

    @Override
    public boolean isAvailableForSync(LiveRelationship relationship, ResourceResolver resourceResolver) {
        LiveCopy liveCopy = relationship.getLiveCopy();
        if (liveCopy == null) {
            LOG.warn("Live copy is null, source path: {}", relationship.getSourcePath());
            return false;
        }
        return isAvailableForSync(
                relationship.getSyncPath(),
                relationship.getTargetPath(),
                liveCopy.getExclusions(),
                resourceResolver
        );
    }

    @Override
    public boolean isAvailableForSync(String syncPath, String targetPath, Set<String> exclusions, ResourceResolver resourceResolver) {
        if (StringUtils.isEmpty(syncPath)) {
            return true;
        }
        if (isInExclusions(StringUtils.removeStart(syncPath, SLASH), exclusions)) {
            return false;
        }
        String syncParent = StringUtils.substringBeforeLast(targetPath, SLASH);
        if (StringUtils.isEmpty(syncParent)) {
            return true;
        }
        return resourceResolver.getResource(syncParent) != null;
    }

    private boolean isInExclusions(String syncPath, Set<String> exclusions) {
        if (exclusions.contains(syncPath)) {
            return true;
        }
        String parent = StringUtils.substringBeforeLast(syncPath, SLASH);
        if (parent.equals(syncPath)) {
            return false;
        }
        return isInExclusions(parent, exclusions);
    }
}