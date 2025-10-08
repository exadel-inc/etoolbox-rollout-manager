package com.exadel.etoolbox.rolloutmanager.core.utils;

import org.apache.sling.event.jobs.consumer.JobExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A facade for {@link JobExecutionContext#log(String, Object...)} that throttles log calls
 * to prevent excessive logging. Messages are queued and batched if they arrive within the
 * throttle interval.
 * <p> This class is thread-safe
 */
public class ThrottledLogger implements AutoCloseable {

    public static final String ENTRY_SEPARATOR = "\n\n";

    private static final long INTERVAL = 4500L;

    private final JobExecutionContext context;
    private final Lock lock = new ReentrantLock();
    private final List<String> messageQueue = new ArrayList<>();

    private long lastLogTime = 0L;

    /**
     * Creates a new throttled logger for the given job execution context.
     *
     * @param context the job execution context to log to
     */
    public ThrottledLogger(JobExecutionContext context) {
        this.context = context;
    }

    /**
     * Logs a message with throttling. If more than {@link #INTERVAL} milliseconds
     * have passed since the last log call, the message is logged immediately. Otherwise, it is
     * queued and will be logged in a batch when the interval has elapsed or when {@link #flush()}
     * is called
     *
     * @param message the message to log
     */
    public void log(String message) {
        lock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            long timeSinceLastLog = currentTime - lastLogTime;

            if (timeSinceLastLog >= INTERVAL) {
                if (!messageQueue.isEmpty()) {
                    // Flush queued messages together with the current one
                    messageQueue.add(message);
                    logBatch();
                } else {
                    // Else, log the current message immediately
                    logImmediately(message);
                }
                lastLogTime = currentTime;
            } else {
                // Queue the message for later
                messageQueue.add(message);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Flushes any remaining messages. This method is called automatically
     * when using try-with-resources
     */
    @Override
    public void close() {
        flush();
    }

    /**
     * Flushes all queued messages to the log immediately as a batch.
     * This method should be called when processing is complete to ensure
     * all messages are logged
     */
    private void flush() {
        lock.lock();
        try {
            if (!messageQueue.isEmpty()) {
                logBatch();
                lastLogTime = System.currentTimeMillis();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Logs a single message immediately without queuing
     *
     * @param message the message to log
     */
    private void logImmediately(String message) {
        context.log("{0}", message);
    }

    /**
     * Logs all queued messages as a single batch and clears the queue
     */
    private void logBatch() {
        if (messageQueue.isEmpty()) {
            return;
        }
        if (messageQueue.size() == 1) {
            logImmediately(messageQueue.get(0));
        } else {
            logImmediately(String.join(ENTRY_SEPARATOR, messageQueue));
        }
        messageQueue.clear();
    }
}

