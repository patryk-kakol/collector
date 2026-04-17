package org.pk.collector.jobs;

import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pk.collector.core.model.JobState;
import org.pk.collector.core.repository.JobStateRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixedDelayJobOrchestratorTest {

    @Mock
    private JobScheduler jobScheduler;

    @Mock
    private JobStateRepository repository;

    @Mock
    private FixedDelayJob mockJob;

    @Mock
    private JobContext jobContext;

    private FixedDelayJobOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        lenient().when(mockJob.getJobId()).thenReturn("test-job");
        lenient().when(mockJob.getDelay()).thenReturn(Duration.ofMinutes(5));
        
        // Re-instantiate directly to pass the mocked List correctly
        orchestrator = new FixedDelayJobOrchestrator(jobScheduler, repository, List.of(mockJob));
    }

    // --- watchAndDispatch() Tests ---

    @Test
    void watchAndDispatch_whenNoStateExists_shouldCreateAndSchedule() {
        // Arrange
        when(repository.findById("test-job")).thenReturn(Optional.empty());

        // Act
        orchestrator.watchAndDispatch();

        // Assert
        ArgumentCaptor<JobState> stateCaptor = ArgumentCaptor.forClass(JobState.class);
        verify(repository).save(stateCaptor.capture());
        
        JobState savedState = stateCaptor.getValue();
        assertTrue(savedState.isRunning());
        assertNotNull(savedState.getCurrentExecutionId());

        verify(jobScheduler).schedule(any(Instant.class), (JobLambda) any());
    }

    @Test
    void watchAndDispatch_whenStateRunningAndStuck_shouldResetAndSchedule() {
        // Arrange
        // Job started 35 minutes ago but never finished (stuck)
        JobState state = new JobState("test-job", Instant.now().minus(Duration.ofMinutes(35)), Instant.EPOCH, true);
        when(repository.findById("test-job")).thenReturn(Optional.of(state));

        // Act
        orchestrator.watchAndDispatch();

        // Assert
        // Because it was reset to false by the watchdog, it should instantly become eligible and start running again
        assertTrue(state.isRunning());
        assertNotNull(state.getCurrentExecutionId());
        
        verify(repository).save(state);
        verify(jobScheduler).schedule(any(Instant.class), (JobLambda) any());
    }

    @Test
    void watchAndDispatch_whenStateRunningAndNotStuck_shouldNotSchedule() {
        // Arrange
        // Job started only 10 minutes ago, still well within the 30-minute limit
        JobState state = new JobState("test-job", Instant.now().minus(Duration.ofMinutes(10)), Instant.EPOCH, true);
        when(repository.findById("test-job")).thenReturn(Optional.of(state));

        // Act
        orchestrator.watchAndDispatch();

        // Assert
        verify(repository, never()).save(any());
        verify(jobScheduler, never()).schedule(any(Instant.class), (JobLambda) any());
    }

    @Test
    void watchAndDispatch_whenStateNotRunningAndNotEligible_shouldNotSchedule() {
        // Arrange
        // Finished 2 minutes ago, but the delay required is 5 minutes
        JobState state = new JobState("test-job", Instant.EPOCH, Instant.now().minus(Duration.ofMinutes(2)), false);
        when(repository.findById("test-job")).thenReturn(Optional.of(state));

        // Act
        orchestrator.watchAndDispatch();

        // Assert
        verify(repository, never()).save(any());
        verify(jobScheduler, never()).schedule(any(Instant.class), (JobLambda) any());
    }

    @Test
    void watchAndDispatch_whenStateNotRunningAndEligible_shouldSchedule() {
        // Arrange
        // Finished 10 minutes ago, easily passing the 5-minute delay wait
        JobState state = new JobState("test-job", Instant.EPOCH, Instant.now().minus(Duration.ofMinutes(10)), false);
        when(repository.findById("test-job")).thenReturn(Optional.of(state));

        // Act
        orchestrator.watchAndDispatch();

        // Assert
        assertTrue(state.isRunning());
        assertNotNull(state.getCurrentExecutionId());
        verify(repository).save(state);
        verify(jobScheduler).schedule(any(Instant.class), (JobLambda) any());
    }

    // --- executeJob() Tests ---

    @Test
    void executeJob_whenStateMissing_shouldThrowException() {
        // Arrange
        when(repository.findById("test-job")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NoSuchElementException.class, () -> 
            orchestrator.executeJob("test-job", "some-id", jobContext));
    }

    @Test
    void executeJob_whenJobNotInList_shouldThrowException() {
        // Arrange
        JobState state = new JobState("unknown-job", Instant.EPOCH, Instant.EPOCH, true);
        state.setCurrentExecutionId("valid-id");

        when(repository.findById("unknown-job")).thenReturn(Optional.of(state));

        // Act & Assert
        assertThrows(NoSuchElementException.class, () -> 
            orchestrator.executeJob("unknown-job", "valid-id", jobContext));
    }

    @Test
    void executeJob_whenExecutionIdMismatch_shouldReturnEarly() throws Exception {
        // Arrange
        JobState state = new JobState("test-job", Instant.EPOCH, Instant.EPOCH, true);
        state.setCurrentExecutionId("valid-id"); // The current DB expectation
        
        when(repository.findById("test-job")).thenReturn(Optional.of(state));

        // Act (we try to run with a stale ID)
        orchestrator.executeJob("test-job", "stale-id", jobContext);

        // Assert
        verify(mockJob, never()).performWork(any());
        verify(repository, never()).save(any());
    }

    @Test
    void executeJob_whenJobMatches_shouldPerformWorkAndUnlock() throws Exception {
        // Arrange
        JobState state = new JobState("test-job", Instant.EPOCH, Instant.EPOCH, true);
        state.setCurrentExecutionId("valid-id");

        // Mockito will seamlessly return this instance for BOTH findById calls (start and finally block)
        when(repository.findById("test-job")).thenReturn(Optional.of(state));

        // Act
        orchestrator.executeJob("test-job", "valid-id", jobContext);

        // Assert
        verify(mockJob).performWork(jobContext);
        verify(repository).save(state);
        assertFalse(state.isRunning(), "The state should be unlocked after successful run");
    }

    @Test
    void executeJob_whenPerformWorkThrowsException_shouldUnlockAndPropagate() throws Exception {
        // Arrange
        JobState state = new JobState("test-job", Instant.EPOCH, Instant.EPOCH, true);
        state.setCurrentExecutionId("valid-id");

        when(repository.findById("test-job")).thenReturn(Optional.of(state));
        doThrow(new RuntimeException("Work crashed!")).when(mockJob).performWork(any());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> 
            orchestrator.executeJob("test-job", "valid-id", jobContext));

        // Verify the finally block still unlocked the job correctly
        verify(repository).save(state);
        assertFalse(state.isRunning());
    }

    @Test
    void executeJob_whenExecutionIdChangesDuringWork_shouldNotUnlock() throws Exception {
        // Arrange
        JobState initialState = new JobState("test-job", Instant.EPOCH, Instant.EPOCH, true);
        initialState.setCurrentExecutionId("valid-id");

        JobState changedState = new JobState("test-job", Instant.EPOCH, Instant.EPOCH, true);
        changedState.setCurrentExecutionId("new-id"); // Represents the watchdog taking over during long execution

        // First findById gets the original state, second findById (in finally block) gets the updated hijacked state
        when(repository.findById("test-job"))
            .thenReturn(Optional.of(initialState))
            .thenReturn(Optional.of(changedState));

        // Act
        orchestrator.executeJob("test-job", "valid-id", jobContext);

        // Assert
        verify(mockJob).performWork(jobContext);
        // Since execution IDs no longer match at the end, it should abandon the unlock attempt
        verify(repository, never()).save(any()); 
    }
}