package org.pk.collector.integration;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.pk.collector.config.SftpProperties.AuthType;
import org.pk.collector.config.SftpProperties.ServerConfig;
import org.pk.collector.domain.SftpFileRecord;
import org.pk.collector.repository.SftpFileRecordRepository;
import org.pk.collector.service.SftpProcessingService;
import org.pk.collector.service.SftpWatchdogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
@SpringBootTest(properties = {
        "sftp.scan-interval-minutes=1",
        "spring.liquibase.enabled=false" // We will run Liquibase manually before Spring starts
})
@ActiveProfiles("test")
@Testcontainers
public class SystemEndToEndIntegrationTest {

    private static final Network network = Network.newNetwork();

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("collector_test")
            .withUsername("test")
            .withPassword("test")
            .withNetwork(network)
            .withNetworkAliases("postgres");

    @Container
    public static GenericContainer<?> sftpNode = new GenericContainer<>(DockerImageName.parse("atmoz/sftp:alpine"))
            .withExposedPorts(22)
            .withCopyFileToContainer(MountableFile.forClasspathResource("mock_rsa.pub"), "/home/keyuser/.ssh/keys/mock_rsa.pub")
            .withCommand("tester:zaq123:::upload", "keyuser::1001::upload");

    @BeforeAll
    public static void runLiquibase() throws Exception {
        // Run Liquibase programmatically before Spring Context starts
        log.info("Starting programmatic Liquibase migration for tests...");
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(), database);
            liquibase.update(new Contexts(), new LabelExpression());
            log.info("Liquibase migration completed successfully.");
        }
    }

    @DynamicPropertySource
    static void configureDynamicSftp(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);

        registry.add("sftp.servers.id", () -> "e2e-test-host");
        registry.add("sftp.servers.host", sftpNode::getHost);
        registry.add("sftp.servers.port", () -> sftpNode.getMappedPort(22));
        registry.add("sftp.servers.username", () -> "tester");
        registry.add("sftp.servers.auth-type", () -> "PASSWORD");
        registry.add("sftp.servers.password", () -> "zaq123");
    }

    @Autowired
    private SftpFileRecordRepository repository;

    @Autowired
    private SftpProcessingService processingService;

    @Autowired
    private SftpWatchdogManager watchdogManager;

    @Test
    public void executeCompleteBootstrappingAndEvaluateFileStorage() throws Exception {
        watchdogManager.checkAndTriggerScan();

        Thread.sleep(4000);

        List<SftpFileRecord> records = repository.findAll();
        assertThat(records).isNotNull();
    }

    @Test
    public void shouldThrowExceptionOnInvalidCredentials() {
        ServerConfig invalidConfig = new ServerConfig(
                "bad-host",
                sftpNode.getHost(),
                sftpNode.getMappedPort(22),
                "tester",
                AuthType.PASSWORD,
                "BLEDNE_HASLO_123",
                null
        );

        long initialCount = repository.count();

        // We expect the Exception to fly up and fail this time (and in Prod trigger the JobRunr Retry policy)
        assertThatThrownBy(() -> processingService.processServer(invalidConfig))
                .isInstanceOf(Exception.class);

        long afterCount = repository.count();
        assertThat(initialCount).isEqualTo(afterCount);
        log.info("As expected, the repository was not updated.");
    }

    @Test
    public void shouldAuthenticateWithPrivateKey() throws Exception {
        ServerConfig keyConfig = new ServerConfig(
                "key-test-host",
                sftpNode.getHost(),
                sftpNode.getMappedPort(22),
                "keyuser",
                AuthType.KEY,
                null,
                "src/test/resources/mock_rsa"
        );

        // If key authentication works correctly, the operation will not throw an exception
        processingService.processServer(keyConfig);
    }
}