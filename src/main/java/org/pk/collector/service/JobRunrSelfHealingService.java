package org.pk.collector.service;

import lombok.extern.slf4j.Slf4j;
import org.jobrunr.server.BackgroundJobServer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobRunrSelfHealingService {

  private final BackgroundJobServer backgroundJobServer;

  public JobRunrSelfHealingService(BackgroundJobServer backgroundJobServer) {
    this.backgroundJobServer = backgroundJobServer;
  }

  @Scheduled(fixedDelay = 30000)
  public void ensureJobRunrIsRunning() {
    if (backgroundJobServer != null && !backgroundJobServer.isRunning()) {
      log.warn("JobRunr BackgroundJobServer is down! Attempting to restart...");
      try {
        backgroundJobServer.start();
        log.info("JobRunr BackgroundJobServer restarted successfully.");
      } catch (Exception e) {
        log.error("Failed to restart JobRunr. Database might still be down.", e);
      }
    }
  }
}
