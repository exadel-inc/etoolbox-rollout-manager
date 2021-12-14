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

package com.exadel.etoolbox.rolloutmanager.core.services;

import com.day.cq.wcm.msm.api.LiveRelationship;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.Set;

/**
 * Provides methods for checking if a live relationship can be synchronized with a blueprint in scope of usage
 * the rollout manager tool.
 */
public interface RelationshipCheckerService {
    /**
     * Checks if a live relationship can be synchronized. Gets the live relationship parameters and performs a check
     * using {@link #isAvailableForSync(String, String, Set, ResourceResolver)}
     *
     * @param relationship     - {@link LiveRelationship} to check
     * @param resourceResolver - {@link ResourceResolver}
     * @return true if the live relationship can be synchronized.
     */
    boolean isAvailableForSync(LiveRelationship relationship, ResourceResolver resourceResolver);

    /**
     * Checks if a live relationship can be synchronized based on its parameters.
     * <p>
     * Let's suppose there are a blueprint page /content/we-retail/language-masters/en and its
     * child /content/we-retail/language-masters/en/experience. The blueprint /content/we-retail/language-masters/en
     * has a live copy (sync root) /content/we-retail/ca/en and the child should be rolled out to
     * /content/we-retail/ca/en/experience.
     *
     * @param syncPath         - the relative path of a relationship from the sync root to the actual resource.
     *                         In the example above the sync path is '/experience'. Empty sync path indicates that
     *                         the live relationship is connected to the blueprint itself (sync root).
     * @param targetPath       - the absolute path of the live sync resource. In the example above the target path
     *                         is '/content/we-retail/ca/en/experience'. The target path consists of sync root
     *                         and sync path.
     * @param exclusions       - relative paths that have been set to be excluded from the LiveCopy configuration
     *                         of the relationship. In the example above if the page
     *                         '/content/we-retail/ca/en/experience' is deleted, the path 'experience' will be added to
     *                         exclusions of '/content/we-retail/ca/en'.
     * @param resourceResolver - {@link ResourceResolver}
     * @return true, if syncPath is empty, or syncPath with all its parents is not in live copy exclusions and a parent
     * resource of the target path exists.
     */
    boolean isAvailableForSync(String syncPath, String targetPath, Set<String> exclusions, ResourceResolver resourceResolver);
}