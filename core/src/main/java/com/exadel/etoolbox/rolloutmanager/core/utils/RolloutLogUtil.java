package com.exadel.etoolbox.rolloutmanager.core.utils;

import com.exadel.etoolbox.rolloutmanager.core.models.LogEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import java.util.List;
import java.util.function.Consumer;

/**
 * Utility class for logging rollout-related events in a structured JSON format
 */
public class RolloutLogUtil {

    private static final Logger LOG = LoggerFactory.getLogger(RolloutLogUtil.class);

    private static final String PROPERTY_PATH = "path";
    private static final String PROPERTY_RESULT = "result";
    private static final String PROPERTY_TYPE = "type";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String RESULT_ERROR = "error";
    private static final String RESULT_SUCCESS = "success";

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
            .add(PROPERTY_PATH, target);
        if (StringUtils.isNotEmpty(errorMessage)) {
            builder.add(PROPERTY_RESULT, RESULT_ERROR);
            builder.add(RESULT_ERROR, errorMessage);
        } else {
            builder.add(PROPERTY_RESULT, RESULT_SUCCESS);
        }
        String message = builder.build().toString();
        logger.accept(message);
    }

    /**
     * Parses a JSON string representation of a log event into a {@link LogEvent} object
     * @param json JSON string representation of a log event
     * @return A {@link LogEvent} object; null if parsing fails
     */
    public static LogEvent getEvent(String json) {
        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(json);
            if (!(jsonNode instanceof ObjectNode)) {
                throw new IllegalArgumentException("Invalid JSON");
            }

            ObjectNode objectNode = (ObjectNode) jsonNode;
            String typeStr = objectNode.has(PROPERTY_TYPE) ? objectNode.get(PROPERTY_TYPE).asText() : null;
            LogEvent.Type type = EnumUtils.getEnumIgnoreCase(LogEvent.Type.class, typeStr);
            String path = objectNode.has(PROPERTY_PATH) ? objectNode.get(PROPERTY_PATH).asText() : null;

            if (type == null || StringUtils.isEmpty(path)) {
                throw new IllegalArgumentException("Missing required properties");
            }

            String resultStr = objectNode.has(PROPERTY_RESULT) ? objectNode.get(PROPERTY_RESULT).asText() : null;
            LogEvent.Result result = RESULT_SUCCESS.equals(resultStr)
                ? LogEvent.Result.SUCCESS
                : LogEvent.Result.ERROR;

            return new LogEvent(type, path, result);

        } catch (JsonProcessingException | IllegalArgumentException e) {
            LOG.error("Failed to parse log event: {}", json, e);
        }
        return null;
    }
}
