package org.pk.collector.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "job_state")
public class JobState {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private String id;

  @Column(name = "last_run_started_at")
  private Instant lastRunStartedAt;

  @Column(name = "last_run_ended_at")
  private Instant lastRunEndedAt;

  @Column(name = "is_running", nullable = false)
  private boolean isRunning;

  @Column(name = "current_execution_id")
  private String currentExecutionId;

  public JobState() {}

  public JobState(String id, Instant lastRunStartedAt, Instant lastRunEndedAt, boolean isRunning) {
    this.id = id;
    this.lastRunStartedAt = lastRunStartedAt;
    this.lastRunEndedAt = lastRunEndedAt;
    this.isRunning = isRunning;
  }
}
