package com.exadel.etoolbox.rolloutmanager.core.utils;

import org.apache.commons.lang3.StringUtils;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import java.util.List;
import java.util.function.Consumer;

/**
 * Utility class for logging rollout-related events in a structured JSON format
 */
public class RolloutLogUtil {

    private static final String PROPERTY_RESULT = "result";
    private static final String PROPERTY_TYPE = "type";

    private RolloutLogUtil() {
    }

    /**
     * Logs the list of rollout targets as a JSON array
     * @param logger  A routine that accepts log messages
     * @param targets List of rollout target paths
     */
    public static void logTargets(Consumer<String> logger, List<String> targets) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        targets.forEach(arrayBuilder::add);
        String message = Json.createObjectBuilder()
            .add(PROPERTY_TYPE, "targets")
            .add("items", arrayBuilder.build())
            .build()
            .toString();
        logger.accept(message);
    }

    /**
     * Logs a rollout event with the specified type and target path
     * @param logger A routine that accepts log messages
     * @param type   Event type
     * @param target Target path
     */
    public static void logEvent(Consumer<String> logger, String type, String target) {
        logEvent(logger, type, target, null);
    }

    /**
     * Logs a rollout event with the specified type, target path, and error message
     * @param logger       A routine that accepts log messages
     * @param type         Event type
     * @param target       Target path
     * @param errorMessage Error message, if any
     */
    public static void logEvent(Consumer<String> logger, String type, String target, String errorMessage) {
        JsonObjectBuilder builder = Json.createObjectBuilder()
            .add(PROPERTY_TYPE, type)
            .add("path", target);
        if (StringUtils.isNotEmpty(errorMessage)) {
            builder.add(PROPERTY_RESULT, "error");
            builder.add("error", errorMessage);
        } else {
            builder.add(PROPERTY_RESULT, "success");
        }
        String message = builder.build().toString();
        logger.accept(message);
    }
}
