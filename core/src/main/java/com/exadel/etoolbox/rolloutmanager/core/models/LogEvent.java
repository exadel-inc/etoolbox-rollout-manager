package com.exadel.etoolbox.rolloutmanager.core.models;

/**
 * Represents a logged event
 */
public class LogEvent {
    private final Type type;
    private final String path;
    private final Result result;

    /**
     * Creates an instance of {@code LogEvent}
     * @param type   Event type
     * @param path   Target path
     * @param result Event result
     */
    public LogEvent(Type type, String path, Result result) {
        this.type = type;
        this.path = path;
        this.result = result;
    }

    /**
     * Retrieves the event type
     * @return {@link Type} value
     */
    public Type getType() {
        return type;
    }

    /**
     * Retrieves the target path
     * @return String value
     */
    public String getPath() {
        return path;
    }

    /**
     * Retrieves the event result
     * @return {@link Result} value
     */
    public Result getResult() {
        return result;
    }

    /**
     * Enumerates possible event results
     */
    public enum Result {
        SUCCESS, ERROR
    }

    /**
     * Enumerates possible event types
     */
    public enum Type {
        ACTIVATION, ROLLOUT
    }
}
