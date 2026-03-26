package org.pk.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.pk.collector.config.SftpProperties;
import org.pk.collector.config.SftpProperties.ServerConfig;
import org.pk.collector.domain.SystemState;
import org.pk.collector.repository.SftpFileBatchRepository;
import org.pk.collector.repository.SystemStateRepository;
import org.pk.collector.sftp.RecursiveSftpScanner;
import org.pk.collector.sftp.SftpClientProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Service
@RequiredArgsConstructor
public class SftpProcessingService {

    private final SftpClientProvider clientProvider;
    private final RecursiveSftpScanner scanner;
    private final SftpFileBatchRepository batchRepository;
    private final SystemStateRepository stateRepository;
    private final SftpProperties properties;

    // Virtual thread allocator native to Java 25. No need to worry about Thread Pinning phenomenon
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Job(name = "Remote recursive SFTP volume scanning", retries = 5)
    public void executeFullScan() throws Exception {
        log.info("Starting full scan for all defined nodes...");
        List<ServerConfig> servers = properties.servers();

        if (servers == null || servers.isEmpty()) {
            log.warn("No servers to scan.");
            return;
        }

        List<Callable<Void>> tasks = servers.stream()
                .map(server -> (Callable<Void>) () -> {
                    processServer(server);
                    return null;
                }).toList();

        try {
            // Evaluation of virtual tasks
            List<Future<Void>> futures = virtualThreadExecutor.invokeAll(tasks);

            // Error unmasking step. Retrieving a fault from among Future structures.
            // Lack of a caught Exception will cause a re-throw upwards to the manager and pass the event to JobRunr Dead Letter.
            for (Future<Void> future : futures) {
                future.get();
            }

            // Setting the completion time point. It will serve the Watchdog as a reference point to determine the 10-minute gap!
            SystemState state = stateRepository.findById("SINGLETON").orElse(new SystemState());
            state.setId("SINGLETON");
            state.setLastCompletedAt(Instant.now());
            stateRepository.save(state);
            log.info("Scan cycle completely successful. System State updated.");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Main process thread interrupted while waiting.", e);
            throw new RuntimeException("Orchestration thread interrupted.", e);
        } catch (ExecutionException e) {
            log.error("One of the servers encountered a stream corruption. Fracture delegated to JobRunr.", e);
            // Informing the planning system that the operation failed execution and must resume Retry
            throw new Exception("Communication error in the virtual process of SFTP servers.", e.getCause());
        }
    }

    public void processServer(ServerConfig serverConfig) throws Exception {
        log.info("Allocating virtual thread to verification node: {}", serverConfig.id());

        // No try-catch block. The exception will fly higher
        clientProvider.executeWithClient(serverConfig, (client, config) -> scanner.scanAll(client, "/", config.id(), batch -> {
            log.info("Saving block of {} records from node: {}", batch.size(), config.id());
            batchRepository.bulkUpsert(batch);
        }));
    }
}
