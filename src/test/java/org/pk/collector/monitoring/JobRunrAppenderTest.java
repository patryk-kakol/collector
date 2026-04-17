package org.pk.collector.monitoring;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.context.JobDashboardLogger;
import org.jobrunr.server.runner.ThreadLocalJobContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobRunrAppenderTest {

    @Mock
    private Filter filter;
    @Mock
    private JobContext jobContext;
    @Mock
    private JobDashboardLogger dashboardLogger;

    private MockedStatic<ThreadLocalJobContext> mockedStaticContext;
    private JobRunrAppender appender;

    @BeforeEach
    void setUp() {
        appender = JobRunrAppender.createAppender("TestAppender", filter);
        appender.start();

        mockedStaticContext = mockStatic(ThreadLocalJobContext.class);
        lenient().when(jobContext.logger()).thenReturn(dashboardLogger);
    }

    @AfterEach
    void tearDown() {
        mockedStaticContext.close();
        appender.stop();
    }

    @Test
    void createAppender_shouldCreateInstance() {
        assertNotNull(appender);
        assertEquals("TestAppender", appender.getName());
        assertTrue(appender.isStarted());
    }

    @Test
    void append_whenNoJobContext_shouldDoNothing() {
        mockedStaticContext.when(ThreadLocalJobContext::hasJobContext).thenReturn(false);
        LogEvent event = mock(LogEvent.class);

        appender.append(event);

        mockedStaticContext.verify(ThreadLocalJobContext::getJobContext, never());
    }

    @Test
    void append_whenInJobContext_shouldLogToDashboard() {
        mockedStaticContext.when(ThreadLocalJobContext::hasJobContext).thenReturn(true);
        mockedStaticContext.when(ThreadLocalJobContext::getJobContext).thenReturn(jobContext);

        appender.append(createLogEvent(Level.INFO, "Info message"));
        appender.append(createLogEvent(Level.WARN, "Warn message"));
        appender.append(createLogEvent(Level.ERROR, "Error message"));
        appender.append(createLogEvent(Level.FATAL, "Fatal message"));
        appender.append(createLogEvent(Level.DEBUG, "Debug message")); // Should default to INFO

        verify(dashboardLogger, times(1)).info(contains("Info message"));
        verify(dashboardLogger, times(1)).info(contains("Debug message"));
        verify(dashboardLogger, times(1)).warn(contains("Warn message"));
        verify(dashboardLogger, times(2)).error(anyString());
    }

    @Test
    void append_whenDashboardLoggerThrowsException_shouldBeCaught() {
        mockedStaticContext.when(ThreadLocalJobContext::hasJobContext).thenReturn(true);
        mockedStaticContext.when(ThreadLocalJobContext::getJobContext).thenReturn(jobContext);
        doThrow(new RuntimeException("Dashboard unavailable")).when(dashboardLogger).info(anyString());

        assertDoesNotThrow(() -> appender.append(createLogEvent(Level.INFO, "test")));
    }

    @Test
    void stop_shouldSetStateCorrectly() {
        assertTrue(appender.stop(100, TimeUnit.MILLISECONDS));
        assertTrue(appender.isStopped());
    }

    private LogEvent createLogEvent(Level level, String message) {
        return Log4jLogEvent.newBuilder()
                .setLoggerName("test-logger")
                .setLevel(level)
                .setMessage(new SimpleMessage(message))
                .build();
    }
}