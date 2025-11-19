package com.exadel.etoolbox.rolloutmanager.core.utils;

import com.exadel.etoolbox.rolloutmanager.core.models.RolloutItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Contains methods to operate with rollout plan representation
 */
public class RolloutPlanUtil {

    private static final Logger LOG = LoggerFactory.getLogger(RolloutPlanUtil.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RolloutPlanUtil() {
    }

    /**
     * Maps JSON array string to the array of {@link RolloutItem} objects
     * @param source JSON array string
     * @return array of {@link RolloutItem} objects; null if mapping fails
     */
    public static RolloutItem[] getItems(String source) {
        try {
            return OBJECT_MAPPER.readValue(source, RolloutItem[].class);
        } catch (IOException | IllegalArgumentException e) {
            LOG.error("Failed to extract items", e);
        }
        return null;
    }
}
