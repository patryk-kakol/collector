package org.pk.collector.jobs;

import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class LocalTimeJob implements FixedDelayJob {

  private static final Logger log =
      new JobRunrDashboardLogger(LoggerFactory.getLogger(LocalTimeJob.class));

  @Override
  public String getJobId() {
    return "local-time-logger";
  }

  @Override
  public Duration getDelay() {
    return Duration.ofMinutes(3);
  }

  @Override
  public void performWork(JobContext jobContext) throws Exception {
    log.info("LocalTimeJob started");
    int sleepMillis = ThreadLocalRandom.current().nextInt(30, 90) * 1000;
    try {
      log.info("LocalTimeJob going to sleep for {}", sleepMillis);
      Thread.sleep(sleepMillis);
      log.info("Executing LocalTimeJob. Local Time: {}", ZonedDateTime.now(ZoneId.systemDefault()));
      log.info("LocalTimeJob finished. Next execution in: {} minutes", getDelay());
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
