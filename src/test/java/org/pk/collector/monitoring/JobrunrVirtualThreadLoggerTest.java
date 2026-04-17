package org.pk.collector.monitoring;

import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.context.JobDashboardLogger;
import org.jobrunr.server.runner.JobRunrVirtualThreadContext;
import org.jobrunr.server.runner.ThreadLocalJobContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobrunrVirtualThreadLoggerTest {

    @Mock
    private JobContext jobContext;
    @Mock
    private JobDashboardLogger dashboardLogger;

    private MockedStatic<ThreadLocalJobContext> mockedStaticJobContext;
    private MockedStatic<JobRunrVirtualThreadContext> mockedStaticVirtualContext;

    private JobrunrVirtualThreadLogger logger;

    @BeforeEach
    void setUp() {
        logger = new JobrunrVirtualThreadLogger(JobrunrVirtualThreadLoggerTest.class);
        mockedStaticJobContext = mockStatic(ThreadLocalJobContext.class);
        mockedStaticVirtualContext = mockStatic(JobRunrVirtualThreadContext.class);

        mockedStaticJobContext.when(ThreadLocalJobContext::hasJobContext).thenReturn(true);
        mockedStaticJobContext.when(ThreadLocalJobContext::getJobContext).thenReturn(jobContext);
        lenient().when(jobContext.logger()).thenReturn(dashboardLogger);
    }

    @AfterEach
    void tearDown() {
        mockedStaticJobContext.close();
        mockedStaticVirtualContext.close();
    }

    @Test
    void wrapWithJobRunrContext_whenContextIsNull_shouldReturnOriginalTasks() {
        Callable<String> task = () -> "done";
        Collection<Callable<String>> tasks = List.of(task);

        List<Callable<String>> resultWithNull = JobrunrVirtualThreadLogger.wrapWithJobRunrContext(tasks, null);
        List<Callable<String>> resultWithNullContext = JobrunrVirtualThreadLogger.wrapWithJobRunrContext(tasks, JobContext.Null);

        assertEquals(tasks, resultWithNull);
        assertEquals(tasks, resultWithNullContext);
    }

    @Test
    void wrapWithJobRunrContext_whenContextIsValid_shouldWrapTask() throws Exception {
        Callable<String> task = () -> "done";
        Collection<Callable<String>> tasks = List.of(task);

        List<Callable<String>> wrappedTasks = JobrunrVirtualThreadLogger.wrapWithJobRunrContext(tasks, jobContext);
        String result = wrappedTasks.getFirst().call();

        assertEquals("done", result);
        mockedStaticVirtualContext.verify(() -> JobRunrVirtualThreadContext.set(jobContext));
        mockedStaticVirtualContext.verify(JobRunrVirtualThreadContext::clear);
    }

    @Test
    void wrapWithJobRunrContext_whenTaskThrowsException_shouldStillClearContext() {
        Callable<String> failingTask = () -> { throw new RuntimeException("Task failed"); };
        Collection<Callable<String>> tasks = List.of(failingTask);

        List<Callable<String>> wrappedTasks = JobrunrVirtualThreadLogger.wrapWithJobRunrContext(tasks, jobContext);

        assertThrows(RuntimeException.class, () -> wrappedTasks.getFirst().call());
        mockedStaticVirtualContext.verify(JobRunrVirtualThreadContext::clear);
    }

    @Test
    void info_shouldLogToDashboard() {
        logger.info("Simple info");
        verify(dashboardLogger).info("Simple info");

        logger.info("Formatted info: {}", "value");
        verify(dashboardLogger).info("Formatted info: value");
    }

    @Test
    void warn_shouldLogToDashboard() {
        logger.warn("Simple warn");
        verify(dashboardLogger).warn("Simple warn");

        logger.warn("Formatted warn: {}", "value");
        verify(dashboardLogger).warn("Formatted warn: value");
    }

    @Test
    void error_shouldLogToDashboard() {
        logger.error("Simple error");
        verify(dashboardLogger).error("Simple error");

        logger.error("Formatted error: {}", "value");
        verify(dashboardLogger).error("Formatted error: value");
    }

    @Test
    void error_withThrowable_shouldLogToDashboardWithStackTrace() {
        Throwable t = new RuntimeException("Test exception");
        logger.error("Error with throwable", t);

        verify(dashboardLogger).error(contains("Error with throwable\njava.lang.RuntimeException: Test exception"));
    }
}