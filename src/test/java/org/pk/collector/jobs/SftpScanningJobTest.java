package org.pk.collector.jobs;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.jobrunr.jobs.context.JobDashboardLogger;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.server.runner.ThreadLocalJobContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pk.collector.config.SftpProperties;
import org.pk.collector.core.repository.SftpFileBatchRepository;
import org.pk.collector.integration.sftp.RecursiveSftpScanner;
import org.pk.collector.integration.sftp.SftpClientProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SftpScanningJobTest {

    @Mock
    private SftpClientProvider clientProvider;

    @Mock
    private RecursiveSftpScanner scanner;

    @Mock
    private SftpFileBatchRepository batchRepository;

    @Mock
    private SftpProperties properties;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private JobContext jobContext;

    @Mock
    private JobDashboardLogger dashboardLogger;

    @InjectMocks
    private SftpScanningJob sftpScanningJob;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sftpScanningJob, "scanningIntervalMinutes", 5L);
        lenient().when(jobContext.logger()).thenReturn(dashboardLogger);
    }

    @Test
    void getJobId_shouldReturnCorrectId() {
        assertEquals("sftp-scanning-job", sftpScanningJob.getJobId());
    }

    @Test
    void getDelay_shouldReturnCorrectDuration() {
        assertEquals(Duration.ofMinutes(5), sftpScanningJob.getDelay());
    }

    @Test
    void performWork_whenNoServersDefined_shouldNotExecuteScan() throws Exception {
        // Arrange
        when(properties.servers()).thenReturn(Collections.emptyList());

        // Act
        sftpScanningJob.performWork(jobContext);

        // Assert
        verify(clientProvider, never()).executeWithClient(any(), any());
        verify(scanner, never()).scanAll(any(), any(), any(), any());
        verify(batchRepository, never()).bulkUpsert(any());
        verify(meterRegistry, never()).gauge(anyString(), anyIterable(), any(Number.class));
    }

    @Test
    void performWork_whenServersDefined_shouldExecuteScanAndRecordMetrics() throws Exception {
        // Arrange
        SftpProperties.ServerConfig server1 = new SftpProperties.ServerConfig(
                "node1", "host1", 22, "user1", SftpProperties.AuthType.PASSWORD, "pass1", null, null
        );
        SftpProperties.ServerConfig server2 = new SftpProperties.ServerConfig(
                "node2", "host2", 22, "user2", SftpProperties.AuthType.KEY, null, "key/path", null
        );
        when(properties.servers()).thenReturn(List.of(server1, server2));

        doAnswer(invocation -> {
            SftpClientProvider.SftpCallback callback = invocation.getArgument(1);
            SftpProperties.ServerConfig cfg = invocation.getArgument(0);
            callback.doWithSftpClient(null, cfg);
            return null;
        }).when(clientProvider).executeWithClient(any(), any());

        // Actively invoke the batch processor consumer so that batchRepository gets called
        doAnswer(invocation -> {
            java.util.function.Consumer<List<org.pk.collector.core.model.SftpFileRecord>> batchProcessor = invocation.getArgument(3);
            batchProcessor.accept(List.of(new org.pk.collector.core.model.SftpFileRecord()));
            return null;
        }).when(scanner).scanAll(any(), any(), any(), any());

        try (MockedStatic<ThreadLocalJobContext> mockedContext = mockStatic(ThreadLocalJobContext.class)) {
            mockedContext.when(ThreadLocalJobContext::hasJobContext).thenReturn(true);
            mockedContext.when(ThreadLocalJobContext::getJobContext).thenReturn(jobContext);

            // Act
            sftpScanningJob.performWork(jobContext);

            // Assert
            verify(clientProvider, times(1)).executeWithClient(eq(server1), any());
            verify(clientProvider, times(1)).executeWithClient(eq(server2), any());
            verify(scanner, times(2)).scanAll(any(), eq("/"), anyString(), any());
            verify(batchRepository, atLeastOnce()).bulkUpsert(any());

            // Verify Metrics for both success scenarios
            verify(meterRegistry).gauge(eq("sftp.connection.timestamp"), eq(List.of(Tag.of("server_id", "node1"), Tag.of("status", "success"))), any(Number.class));
            verify(meterRegistry).gauge(eq("sftp.connection.timestamp"), eq(List.of(Tag.of("server_id", "node2"), Tag.of("status", "success"))), any(Number.class));
        }
    }

    @Test
    void performWork_whenScannerThrowsException_shouldPropagateAsRuntimeExceptionAndRecordFailureMetric() throws Exception {
        // Arrange
        SftpProperties.ServerConfig server = new SftpProperties.ServerConfig(
                "node1", "host1", 22, "user1", SftpProperties.AuthType.PASSWORD, "pass1", null, null
        );
        when(properties.servers()).thenReturn(List.of(server));

        doAnswer(invocation -> {
            SftpClientProvider.SftpCallback callback = invocation.getArgument(1);
            callback.doWithSftpClient(null, server);
            return null;
        }).when(clientProvider).executeWithClient(any(), any());

        doThrow(new RuntimeException("Scan failed")).when(scanner).scanAll(any(), any(), any(), any());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () ->
            sftpScanningJob.performWork(jobContext));
        assertTrue(exception.getMessage().contains("Communication error"));
        assertTrue(exception.getCause().getMessage().contains("Scan failed"));

        // Verify Metrics for failure
        verify(meterRegistry).gauge(eq("sftp.connection.timestamp"), eq(List.of(Tag.of("server_id", "node1"), Tag.of("status", "failure"))), any(Number.class));
    }

    @Test
    void performWork_whenInterrupted_shouldHandleInterruption() {
        // Arrange
        SftpProperties.ServerConfig server = new SftpProperties.ServerConfig(
                "node1", "host1", 22, "user1", SftpProperties.AuthType.PASSWORD, "pass1", null, null
        );
        when(properties.servers()).thenReturn(List.of(server));

        // Act & Assert
        Thread.currentThread().interrupt();
        Exception exception = assertThrows(Exception.class, () ->
            sftpScanningJob.performWork(jobContext));
        assertTrue(exception.getMessage().contains("Orchestration thread interrupted"));
        assertTrue(Thread.interrupted(), "Main thread should remain interrupted");
    }

    @Test
    void performWork_whenExecutionExceptionOccurs_shouldPropagateCauseAndRecordFailureMetric() throws Exception {
        // Arrange
        SftpProperties.ServerConfig server = new SftpProperties.ServerConfig(
                "node1", "host1", 22, "user1", SftpProperties.AuthType.PASSWORD, "pass1", null, null
        );
        when(properties.servers()).thenReturn(List.of(server));

        doAnswer(invocation -> {
            SftpClientProvider.SftpCallback callback = invocation.getArgument(1);
            callback.doWithSftpClient(null, server);
            return null;
        }).when(clientProvider).executeWithClient(any(), any());

        IOException originalException = new IOException("Connection timeout");
        doThrow(originalException).when(scanner).scanAll(any(), any(), any(), any());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () ->
            sftpScanningJob.performWork(jobContext));
        assertEquals("Communication error in the virtual process of SFTP servers.", exception.getMessage());
        assertEquals(originalException, exception.getCause());
        
        // Verify Metrics for failure
        verify(meterRegistry).gauge(eq("sftp.connection.timestamp"), eq(List.of(Tag.of("server_id", "node1"), Tag.of("status", "failure"))), any(Number.class));
    }

    @Test
    void scanServer_shouldExecuteScanWithCorrectParameters() throws Exception {
        // Arrange
        SftpProperties.ServerConfig server = new SftpProperties.ServerConfig(
                "test-server", "sftp.example.com", 22, "user", SftpProperties.AuthType.PASSWORD, "pass", null, null
        );

        doAnswer(invocation -> {
            SftpClientProvider.SftpCallback callback = invocation.getArgument(1);
            callback.doWithSftpClient(null, server); // Mock callback behavior seamlessly 
            return null;
        }).when(clientProvider).executeWithClient(any(), any());

        // Act
        sftpScanningJob.scanServer(server);

        // Assert
        verify(clientProvider).executeWithClient(eq(server), any());
        verify(scanner).scanAll(any(), eq("/"), eq("test-server"), any());
    }
}
