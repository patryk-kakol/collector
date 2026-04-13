package org.pk.collector.jobs;

import lombok.RequiredArgsConstructor;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.jobrunr.scheduling.JobScheduler;
import org.pk.collector.core.model.JobState;
import org.pk.collector.core.repository.JobStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FixedDelayJobOrchestrator {

  private static final Logger log =
      new JobRunrDashboardLogger(LoggerFactory.getLogger(FixedDelayJobOrchestrator.class));

  private final JobScheduler jobScheduler;
  private final JobStateRepository repository;
  private final List<FixedDelayJob> jobs;

  @Recurring(id = "fixed-delay-job-orchestrator", cron = "* * * * *")
  @Transactional
  public void watchAndDispatch() {
    Instant now = Instant.now();

    for (FixedDelayJob job : jobs) {
      JobState state =
          repository
              .findById(job.getJobId())
              .orElseGet(() -> new JobState(job.getJobId(), Instant.EPOCH, Instant.EPOCH, false));

      if (state.isRunning()
          && state.getLastRunStartedAt().isBefore(now.minus(Duration.ofMinutes(30)))) {
        state.setRunning(false);
      }

      if (!state.isRunning()) {
        Instant eligibleTime = state.getLastRunEndedAt().plus(job.getDelay());
        if (!now.isBefore(eligibleTime)) {
          String newExecutionId = UUID.randomUUID().toString();
          String jobId = job.getJobId();
          state.setRunning(true);
          state.setLastRunStartedAt(now);
          state.setCurrentExecutionId(newExecutionId);
          repository.save(state);

          Instant executionTime = now.isAfter(eligibleTime) ? now : eligibleTime;
          jobScheduler.schedule(
              executionTime, () -> executeJob(jobId, newExecutionId, JobContext.Null));
        }
      }
    }
  }

  @Job(name = "Job: %0")
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void executeJob(String jobId, String executionId, JobContext jobContext) throws Exception {
    JobState state = repository.findById(jobId).orElseThrow();
    /* Kill ghost retries from application restarts */
    if (!executionId.equals(state.getCurrentExecutionId())) {
      log.info("Skipping stale JobRunr retry for [{}]. Watchdog has moved on.", jobId);
      return;
    }

    /* Find and execute the actual work */
    FixedDelayJob job =
        jobs.stream().filter(j -> j.getJobId().equals(jobId)).findFirst().orElseThrow();

    try {
      job.performWork(jobContext);
    } finally {
      /* Only unlock if this is still the active execution */
      JobState endState = repository.findById(jobId).orElseThrow();
      if (executionId.equals(endState.getCurrentExecutionId())) {
        endState.setRunning(false);
        endState.setLastRunEndedAt(Instant.now());
        repository.save(endState);
      }
    }
  }
}
