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
public class JapanTimeJob implements FixedDelayJob {

  private static final Logger log =
      new JobRunrDashboardLogger(LoggerFactory.getLogger(JapanTimeJob.class));

  @Override
  public String getJobId() {
    return "japan-time-logger";
  }

  @Override
  public Duration getDelay() {
    return Duration.ofMinutes(2);
  }

  @Override
  public void performWork(JobContext jobContext) throws Exception {
    log.info("JapanTimeJob started");
    int sleepMillis = ThreadLocalRandom.current().nextInt(30, 90) * 1000;
    try {
      log.info("JapanTimeJob going to sleep for: " + sleepMillis);
      Thread.sleep(sleepMillis);
      log.info(
          "Executing JapanTimeJob. Japan Time: {}", ZonedDateTime.now(ZoneId.of("Asia/Tokyo")));
      log.info("JapanTimeJob finished. Next execution in: {} minutes", getDelay());
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
