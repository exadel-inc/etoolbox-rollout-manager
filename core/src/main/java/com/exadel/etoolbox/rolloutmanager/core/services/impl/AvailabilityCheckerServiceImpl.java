package com.exadel.etoolbox.rolloutmanager.core.services.impl;

import com.day.cq.wcm.msm.api.LiveCopy;
import com.day.cq.wcm.msm.api.LiveRelationship;
import com.exadel.etoolbox.rolloutmanager.core.services.AvailabilityCheckerService;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@Component(service = AvailabilityCheckerService.class)
public class AvailabilityCheckerServiceImpl implements AvailabilityCheckerService {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityCheckerServiceImpl.class);

    @Override
    public boolean isAvailableForRollout(LiveRelationship relationship, ResourceResolver resourceResolver) {
        LiveCopy liveCopy = relationship.getLiveCopy();
        if (liveCopy == null) {
            LOG.warn("live copy is null, source path: {}", relationship.getSourcePath());
            return false;
        }
        return isAvailableForRollout(
                relationship.getSyncPath(),
                relationship.getTargetPath(),
                liveCopy.getExclusions(),
                resourceResolver
        );
    }

    @Override
    public boolean isAvailableForRollout(String syncPath, String targetPath, Set<String> exclusions, ResourceResolver resourceResolver) {
        if (StringUtils.isEmpty(syncPath)) {
            return true;
        }
        if (isInExclusions(StringUtils.removeStart(syncPath, "/"), exclusions)) {
            return false;
        }
        String syncParent = StringUtils.substringBeforeLast(targetPath, "/");
        if (StringUtils.isEmpty(syncParent)) {
            return true;
        }
        return resourceResolver.getResource(syncParent) != null;
    }

    private boolean isInExclusions(String syncPath, Set<String> exclusions) {
        if (exclusions.contains(syncPath)) {
            return true;
        } else {
            String parent = StringUtils.substringBeforeLast(syncPath, "/");
            if (parent.equals(syncPath)) {
                return false;
            } else {
                return isInExclusions(parent, exclusions);
            }
        }
    }
}