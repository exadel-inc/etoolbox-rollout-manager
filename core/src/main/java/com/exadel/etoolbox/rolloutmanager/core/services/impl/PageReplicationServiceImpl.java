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

import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.Replicator;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.msm.api.LiveRelationshipManager;
import com.exadel.etoolbox.rolloutmanager.core.models.RolloutItem;
import com.exadel.etoolbox.rolloutmanager.core.models.RolloutStatus;
import com.exadel.etoolbox.rolloutmanager.core.services.PageReplicationService;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.event.jobs.JobManager;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Session;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component(service = PageReplicationService.class)
@Designate(ocd = PageReplicationServiceImpl.Configuration.class)
public class PageReplicationServiceImpl implements PageReplicationService {
    private static final Logger LOG = LoggerFactory.getLogger(PageReplicationServiceImpl.class);

    @ObjectClassDefinition(name = "EToolbox Page Replication Service Configuration")
    @interface Configuration {

        @AttributeDefinition(
                name = "Pool size",
                description = "The number of Threads in the pool")
        int poolSize() default 5;
    }

    @Activate
    private PageReplicationServiceImpl.Configuration config;

    @Reference
    private LiveRelationshipManager liveRelationshipManager;

    @Reference
    private JobManager jobManager;

    @Reference
    private Replicator replicator;

    public List<RolloutStatus> replicateItems(ResourceResolver resourceResolver, RolloutItem[] items, PageManager pageManager) {
        return Arrays.stream(items)
                .collect(Collectors.groupingBy(RolloutItem::getDepth))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .flatMap(sortedByDepthItems -> replicateSortedByDepthItems(resourceResolver, sortedByDepthItems, pageManager))
                .collect(Collectors.toList());
    }

    private Stream<RolloutStatus> replicateSortedByDepthItems(ResourceResolver resourceResolver, List<RolloutItem> items, PageManager pageManager) {
        ExecutorService executorService = Executors.newFixedThreadPool(config.poolSize());
        return items.stream()
                .filter(item -> StringUtils.isNotBlank(item.getTarget()))
                .map(item -> CompletableFuture.supplyAsync(() -> replicate(resourceResolver, item, pageManager), executorService))
                .collect(Collectors.toList())
                .stream()
                .map(CompletableFuture::join);
    }

    private RolloutStatus replicate(ResourceResolver resourceResolver, RolloutItem targetItem, PageManager pageManager) {

        String targetPath = targetItem.getTarget();
        RolloutStatus status = new RolloutStatus(targetPath);

        Optional<Page> targetPage = Optional.ofNullable(pageManager.getPage(targetPath));
        Session session = resourceResolver.adaptTo(Session.class);
        if (!targetPage.isPresent() || ObjectUtils.isEmpty(session)) {
            status.setSuccess(false);
            LOG.warn("Replication failed - target page is null, page path: {}", targetPath);
            return status;
        }
        try {
            replicator.replicate(session, ReplicationActionType.ACTIVATE, targetPath);
        } catch (ReplicationException ex) {
            LOG.error("Exception during page replication", ex);
        }
        status.setSuccess(true);
        return status;
    }
}