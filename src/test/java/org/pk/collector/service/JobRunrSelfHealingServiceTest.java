package org.pk.collector.service;

import org.jobrunr.server.BackgroundJobServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobRunrSelfHealingServiceTest {

    @Mock
    private BackgroundJobServer backgroundJobServer;

    @InjectMocks
    private JobRunrSelfHealingService selfHealingService;

    @Test
    void ensureJobRunrIsRunning_whenServerIsNull_shouldDoNothing() {
        // Arrange
        JobRunrSelfHealingService serviceWithNullServer = new JobRunrSelfHealingService(null);

        // Act & Assert
        assertDoesNotThrow(serviceWithNullServer::ensureJobRunrIsRunning);
    }

    @Test
    void ensureJobRunrIsRunning_whenServerIsRunning_shouldNotAttemptRestart() {
        // Arrange
        when(backgroundJobServer.isRunning()).thenReturn(true);

        // Act
        selfHealingService.ensureJobRunrIsRunning();

        // Assert
        verify(backgroundJobServer, never()).start();
    }

    @Test
    void ensureJobRunrIsRunning_whenServerIsNotRunning_shouldRestartSuccessfully() {
        // Arrange
        when(backgroundJobServer.isRunning()).thenReturn(false);

        // Act
        selfHealingService.ensureJobRunrIsRunning();

        // Assert
        verify(backgroundJobServer).start();
    }

    @Test
    void ensureJobRunrIsRunning_whenRestartThrowsException_shouldCatchExceptionAndLog() {
        // Arrange
        when(backgroundJobServer.isRunning()).thenReturn(false);
        doThrow(new RuntimeException("Simulated database failure")).when(backgroundJobServer).start();

        // Act & Assert
        // The exception should be caught internally by the service
        assertDoesNotThrow(() -> selfHealingService.ensureJobRunrIsRunning());
        verify(backgroundJobServer).start();
    }
}