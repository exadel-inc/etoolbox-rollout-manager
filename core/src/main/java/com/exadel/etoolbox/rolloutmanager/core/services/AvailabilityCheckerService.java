package com.exadel.etoolbox.rolloutmanager.core.services;

import com.day.cq.wcm.msm.api.LiveRelationship;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.Set;

public interface AvailabilityCheckerService {
    boolean isAvailableForRollout(LiveRelationship relationship, ResourceResolver resourceResolver);

    boolean isAvailableForRollout(String syncPath, String targetPath, Set<String> exclusions, ResourceResolver resourceResolver);
}