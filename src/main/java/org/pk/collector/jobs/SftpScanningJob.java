package org.pk.collector.jobs;

import lombok.RequiredArgsConstructor;
import org.jobrunr.jobs.context.JobContext;
import org.pk.collector.config.SftpProperties;
import org.pk.collector.core.repository.SftpFileBatchRepository;
import org.pk.collector.integration.sftp.RecursiveSftpScanner;
import org.pk.collector.integration.sftp.SftpClientProvider;
import org.pk.collector.monitoring.JobrunrVirtualThreadLogger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

@Component
@RequiredArgsConstructor
public class SftpScanningJob implements FixedDelayJob {

  private static final JobrunrVirtualThreadLogger log =
      new JobrunrVirtualThreadLogger(SftpScanningJob.class);

  private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

  private final SftpClientProvider clientProvider;
  private final RecursiveSftpScanner scanner;
  private final SftpFileBatchRepository batchRepository;
  private final SftpProperties properties;

  @Override
  public String getJobId() {
    return "sftp-scanning-job";
  }

  @Override
  public Duration getDelay() {
    return Duration.ofMinutes(2);
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

    List<Callable<Void>> tasks =
        servers.stream()
            .map(
                serverConfig ->
                    (Callable<Void>)
                        () -> {
                          scanServer(serverConfig);
                          return null;
                        })
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
      log.error(
          "One of the servers encountered a stream corruption. Fracture delegated to JobRunr.", e);
      throw new Exception(
          "Communication error in the virtual process of SFTP servers.", e.getCause());
    }
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
}
