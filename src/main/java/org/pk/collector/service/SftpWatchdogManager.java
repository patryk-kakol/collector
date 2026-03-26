package org.pk.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.jobrunr.scheduling.JobScheduler;

import org.pk.collector.config.SftpProperties;
import org.pk.collector.domain.SystemState;
import org.pk.collector.repository.SystemStateRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SftpWatchdogManager {

    private final JobScheduler jobScheduler;
    private final SystemStateRepository stateRepository;
    private final SftpProperties properties;
    private final SftpProcessingService processingService;

    /**
     * Watchdog pattern. The function wakes up every minute and evaluates the system state.
     */
    @Recurring(id = "sftp-scanner-watchdog", cron = "*/1 * * * *")
    @Job(name = "Watchdog: SFTP scanning interval overseer")
    public void checkAndTriggerScan() {
        Instant lastCompleted = stateRepository.findById("SINGLETON")
                .map(SystemState::getLastCompletedAt)
                .orElse(Instant.EPOCH);

        Instant nextAllowedRun = lastCompleted.plus(properties.scanIntervalMinutes(), ChronoUnit.MINUTES);

        if (Instant.now().isAfter(nextAllowedRun)) {
            log.info("Watchdog: Scanning need identified (last completion: {}).", lastCompleted);

            // Guarantee of idempotence - prevents multiple entry by calculating UUID
            String deterministicIdSeed = "scan-cycle-" + lastCompleted.toEpochMilli();
            UUID idempotentJobId = UUID.nameUUIDFromBytes(deterministicIdSeed.getBytes(StandardCharsets.UTF_8));

            jobScheduler.enqueue(idempotentJobId, processingService::executeFullScan);
        } else {
            log.debug("Watchdog: Waiting interval in progress. Next scanning allowed after: {}", nextAllowedRun);
        }
    }
}
