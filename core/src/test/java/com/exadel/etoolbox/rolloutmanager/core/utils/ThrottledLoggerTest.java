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

package com.exadel.etoolbox.rolloutmanager.core.utils;

import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ThrottledLoggerTest {

    private static final String TEST_MESSAGE_1 = "Test message 1";
    private static final String TEST_MESSAGE_2 = "Test message 2";
    private static final String TEST_MESSAGE_3 = "Test message 3";

    @Mock
    private JobExecutionContext context;

    private ThrottledLogger fixture;

    @BeforeEach
    void setup() {
        fixture = new ThrottledLogger(context);
    }

    @Test
    void log_FirstMessage_LogsImmediately() {
        fixture.log(TEST_MESSAGE_1);

        verify(context, times(1)).log(eq("{0}"), eq(TEST_MESSAGE_1));
    }

    @Test
    void log_MultipleMessagesWithinThrottleInterval_QueuesMessages() throws InterruptedException {
        fixture.log(TEST_MESSAGE_1);
        Thread.sleep(100);
        fixture.log(TEST_MESSAGE_2);
        Thread.sleep(100);
        fixture.log(TEST_MESSAGE_3);

        verify(context, times(1)).log(eq("{0}"), eq(TEST_MESSAGE_1));
        verify(context, never()).log(eq("{0}"), eq(TEST_MESSAGE_2));
        verify(context, never()).log(eq("{0}"), eq(TEST_MESSAGE_3));
    }

    @Test
    void log_MessagesAfterThrottleInterval_LogsBatch() throws InterruptedException {
        fixture.log(TEST_MESSAGE_1);
        Thread.sleep(100);
        fixture.log(TEST_MESSAGE_2);
        Thread.sleep(5100);
        fixture.log(TEST_MESSAGE_3);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(context, times(2)).log(eq("{0}"), messageCaptor.capture());

        assertEquals(TEST_MESSAGE_1, messageCaptor.getAllValues().get(0));
        String batchMessage = messageCaptor.getAllValues().get(1);
        assertTrue(batchMessage.contains(TEST_MESSAGE_2));
        assertTrue(batchMessage.contains(TEST_MESSAGE_3));
    }

    @Test
    void close_FlushesQueuedMessages() {
        fixture.log(TEST_MESSAGE_1);
        fixture.log(TEST_MESSAGE_2);

        verify(context, times(1)).log(eq("{0}"), eq(TEST_MESSAGE_1));

        fixture.close();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(context, times(2)).log(eq("{0}"), messageCaptor.capture());

        String flushedMessage = messageCaptor.getAllValues().get(1);
        assertEquals(TEST_MESSAGE_2, flushedMessage);
    }

    @Test
    void close_WithMultipleQueuedMessages_FlushesAsBatch() {
        fixture.log(TEST_MESSAGE_1);
        fixture.log(TEST_MESSAGE_2);
        fixture.log(TEST_MESSAGE_3);

        verify(context, times(1)).log(eq("{0}"), eq(TEST_MESSAGE_1));

        fixture.close();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(context, times(2)).log(eq("{0}"), messageCaptor.capture());

        String batchMessage = messageCaptor.getAllValues().get(1);
        assertTrue(batchMessage.contains(TEST_MESSAGE_2));
        assertTrue(batchMessage.contains(TEST_MESSAGE_3));
    }

    @Test
    void close_WithEmptyQueue_DoesNotLog() {
        fixture.log(TEST_MESSAGE_1);

        verify(context, times(1)).log(eq("{0}"), eq(TEST_MESSAGE_1));

        fixture.close();

        verify(context, times(1)).log(any(), any());
    }

    @Test
    void log_ConcurrentAccess_ThreadSafe() throws InterruptedException {
        Thread thread1 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                fixture.log("Thread 1 message " + i);
            }
        });

        Thread thread2 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                fixture.log("Thread 2 message " + i);
            }
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        fixture.close();

        verify(context, times(2)).log(eq("{0}"), any());
    }
}

