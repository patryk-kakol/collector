package org.pk.collector.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JobStateTest {

    @Test
    void testNoArgsConstructor() {
        JobState jobState = new JobState();
        assertNull(jobState.getId());
        assertNull(jobState.getLastRunStartedAt());
        assertNull(jobState.getLastRunEndedAt());
        assertFalse(jobState.isRunning());
        assertNull(jobState.getCurrentExecutionId());
    }

    @Test
    void testAllArgsConstructor() {
        String id = "job1";
        Instant startedAt = Instant.now().minusSeconds(60);
        Instant endedAt = Instant.now();
        boolean isRunning = false;

        JobState jobState = new JobState(id, startedAt, endedAt, isRunning);

        assertEquals(id, jobState.getId());
        assertEquals(startedAt, jobState.getLastRunStartedAt());
        assertEquals(endedAt, jobState.getLastRunEndedAt());
        assertEquals(isRunning, jobState.isRunning());
        assertNull(jobState.getCurrentExecutionId()); // Should be null as it's not set by constructor
    }

    @Test
    void testSettersAndGetters() {
        JobState jobState = new JobState();
        String id = "job2";
        Instant startedAt = Instant.now().minusSeconds(120);
        Instant endedAt = Instant.now().minusSeconds(10);
        boolean isRunning = true;
        String executionId = "exec-123";

        jobState.setId(id);
        jobState.setLastRunStartedAt(startedAt);
        jobState.setLastRunEndedAt(endedAt);
        jobState.setRunning(isRunning);
        jobState.setCurrentExecutionId(executionId);

        assertEquals(id, jobState.getId());
        assertEquals(startedAt, jobState.getLastRunStartedAt());
        assertEquals(endedAt, jobState.getLastRunEndedAt());
        assertTrue(jobState.isRunning());
        assertEquals(executionId, jobState.getCurrentExecutionId());
    }
}
