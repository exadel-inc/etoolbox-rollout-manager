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

import com.day.cq.wcm.api.PageManager;
import com.exadel.etoolbox.rolloutmanager.core.models.RolloutItem;
import com.exadel.etoolbox.rolloutmanager.core.models.RolloutStatus;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.List;

/**
 * Provides methods for checking if a live relationship can be synchronized with a blueprint in scope of usage
 * the rollout manager tool.
 */
public interface PageReplicationService {
    List<RolloutStatus> replicateItems(ResourceResolver resourceResolver, RolloutItem[] items, PageManager pageManager, boolean isDeep);
}