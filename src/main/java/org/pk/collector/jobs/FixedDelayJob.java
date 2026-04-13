package org.pk.collector.jobs;

import org.jobrunr.jobs.context.JobContext;

import java.time.Duration;

public interface FixedDelayJob {
  String getJobId();

  Duration getDelay();

  void performWork(JobContext jobContext) throws Exception;
}
