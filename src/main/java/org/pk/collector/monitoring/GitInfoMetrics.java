package org.pk.collector.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
public class GitInfoMetrics {

    private final MeterRegistry meterRegistry;
    private final GitProperties gitProperties;

    public GitInfoMetrics(MeterRegistry meterRegistry, @Autowired(required = false) GitProperties gitProperties) {
        this.meterRegistry = meterRegistry;
        this.gitProperties = gitProperties;
    }

    @PostConstruct
    public void registerGitMetrics() {
        if (gitProperties != null) {
            String commitId = gitProperties.getCommitId();
            String branch = gitProperties.getBranch();

            String finalCommit = commitId != null ? commitId : "unknown";
            String finalBranch = branch != null ? branch : "unknown";

            log.info("Application starting with Git Branch: {}, Commit: {}", finalBranch, finalCommit);

            Gauge.builder("git.info", () -> 1)
                    .description("Git information for the application")
                    .tag("commit", finalCommit)
                    .tag("branch", finalBranch)
                    .register(meterRegistry);
        } else {
            log.warn("GitProperties not available. Make sure git.properties is generated and placed in classpath.");
        }
    }
}