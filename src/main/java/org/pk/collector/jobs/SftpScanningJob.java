package org.pk.collector.jobs;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.RequiredArgsConstructor;
import org.jobrunr.jobs.context.JobContext;
import org.pk.collector.config.SftpProperties;
import org.pk.collector.core.repository.SftpFileBatchRepository;
import org.pk.collector.integration.sftp.RecursiveSftpScanner;
import org.pk.collector.integration.sftp.SftpClientProvider;
import org.pk.collector.monitoring.JobrunrVirtualThreadLogger;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class SftpScanningJob implements FixedDelayJob {

  private static final JobrunrVirtualThreadLogger log =
      new JobrunrVirtualThreadLogger(SftpScanningJob.class);

  private final ExecutorService virtualThreadExecutor = Executors.newThreadPerTaskExecutor(
          Thread.ofVirtual().name("sftp-scan-", 1).factory()
  );
  private final ConcurrentHashMap<String, AtomicLong> gaugeMap = new ConcurrentHashMap<>();

  private final SftpClientProvider clientProvider;
  private final RecursiveSftpScanner scanner;
  private final SftpFileBatchRepository batchRepository;
  private final SftpProperties properties;
  private final MeterRegistry meterRegistry;

  @Value("${collector.jobs.sftp-scanning-interval-minutes:2}")
  private long scanningIntervalMinutes;

  @Override
  public String getJobId() {
    return "sftp-scanning-job";
  }

  @Override
  public Duration getDelay() {
    return Duration.ofMinutes(scanningIntervalMinutes);
  }

  @Override
  public void performWork(JobContext jobContext) throws Exception {
    log.info("SftpScanningJob started");
    log.info("Starting full scan for all defined nodes...");
    List<SftpProperties.ServerConfig> servers = properties.servers();

    if (servers == null || servers.isEmpty()) {
      log.warn("No servers to scan.");
      return;
    }

    AtomicReference<Exception> caughtException = new AtomicReference<>();

    List<Callable<Void>> tasks =
        servers.stream()
            .map(serverConfig -> createScanTask(serverConfig, caughtException))
            .toList();

    List<Callable<Void>> contextAwareTasks =
        JobrunrVirtualThreadLogger.wrapWithJobRunrContext(tasks, jobContext);

    try {
      List<Future<Void>> futures = virtualThreadExecutor.invokeAll(contextAwareTasks);
      for (Future<Void> future : futures) {
        future.get();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Main process thread interrupted while waiting.", e);
      throw new RuntimeException("Orchestration thread interrupted.", e);
    } catch (ExecutionException e) {
      log.error("A thread execution error occurred.", e);
      throw new Exception(
          "Thread execution error in the virtual process of SFTP servers.", e.getCause());
    }

    if (caughtException.get() != null) {
      log.error(
          "One of the servers encountered a stream corruption. Fracture delegated to JobRunr.",
          caughtException.get());
      throw new Exception(
          "Communication error in the virtual process of SFTP servers.", caughtException.get());
    }
  }

  private Callable<Void> createScanTask(
      SftpProperties.ServerConfig serverConfig, AtomicReference<Exception> caughtException) {
    return () -> {
      // Put MDC context so thread name and serverId can be tracked natively across standard logs 
      try (MDC.MDCCloseable ignored = MDC.putCloseable("serverId", serverConfig.id())) {
          scanServer(serverConfig);
          recordSuccessMetric(serverConfig.id());
      } catch (Exception ex) {
          recordFailureMetric(serverConfig.id());
          caughtException.compareAndSet(null, ex); // Keep the first exception
          throw ex; // Re-throw to ensure the Future resolves with an exception
      }
      return null;
    };
  }

  public void scanServer(SftpProperties.ServerConfig serverConfig) throws Exception {
    log.info("Allocating virtual thread to verification node: {}", serverConfig.id());
    clientProvider.executeWithClient(
        serverConfig,
        (client, config) ->
            scanner.scanAll(
                client,
                "/",
                config.id(),
                batch -> {
                  log.info("Saving block of {} records from node: {}", batch.size(), config.id());
                  batchRepository.bulkUpsert(batch);
                }));
  }

  private void recordSuccessMetric(String serverId) {
    getOrCreateGauge(serverId, "success").set(System.currentTimeMillis());
  }

  private void recordFailureMetric(String serverId) {
    getOrCreateGauge(serverId, "failure").set(System.currentTimeMillis());
  }

  private AtomicLong getOrCreateGauge(String serverId, String status) {
    String key = serverId + "-" + status;
    return gaugeMap.computeIfAbsent(key, ignored -> {
        AtomicLong newGauge = new AtomicLong(0);
        meterRegistry.gauge(
            "sftp.connection.timestamp",
            List.of(Tag.of("server_id", serverId), Tag.of("status", status)),
            newGauge
        );
        return newGauge;
    });
  }
}
