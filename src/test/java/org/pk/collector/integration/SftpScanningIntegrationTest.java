package org.pk.collector.integration;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.context.JobDashboardLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.pk.collector.config.SftpProperties;
import org.pk.collector.core.model.SftpFileRecord;
import org.pk.collector.core.repository.SftpFileBatchRepository;
import org.pk.collector.jobs.SftpScanningJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(
    properties = {
      // Use H2 memory database
      "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "spring.liquibase.enabled=true",
      "org.jobrunr.database.type=sql",
      "spring.main.allow-bean-definition-overriding=true"
    })
@Import(SftpScanningIntegrationTest.H2RepositoryOverrideConfig.class)
class SftpScanningIntegrationTest {

  private static SshServer sshServer;
  private static Path serverRoot;
  private static Path privateKeyPath;

  @Autowired private SftpScanningJob sftpScanningJob;

  @Autowired private JdbcTemplate jdbcTemplate;

  @TestConfiguration
  static class H2RepositoryOverrideConfig {

    @Bean
    @Primary
    public SftpFileBatchRepository sftpFileBatchRepository(JdbcTemplate jdbcTemplate) {
      return new SftpFileBatchRepository(jdbcTemplate) {
        @Override
        public void bulkUpsert(List<SftpFileRecord> records) {
          // H2 MERGE INTO syntax (database-agnostic upsert replacement for testing)
          String sql =
              "MERGE INTO sftp_file_registry (id, server_id, file_name, file_path, file_size, creation_timestamp, modification_timestamp, last_scanned_at) "
                  + "KEY(id) "
                  + "VALUES (?,?,?,?,?,?,?,?)";

          jdbcTemplate.batchUpdate(
              sql,
              records,
              1000,
              (ps, record) -> {
                ps.setString(1, record.getId());
                ps.setString(2, record.getServerId());
                ps.setString(3, record.getFileName());
                ps.setString(4, record.getFilePath());
                ps.setObject(5, record.getFileSize(), Types.BIGINT);
                ps.setTimestamp(
                    6,
                    record.getCreationTimestamp() != null
                        ? Timestamp.from(record.getCreationTimestamp())
                        : null);
                ps.setTimestamp(
                    7,
                    record.getModificationTimestamp() != null
                        ? Timestamp.from(record.getModificationTimestamp())
                        : null);
                ps.setTimestamp(8, Timestamp.from(record.getLastScannedAt()));
              });
        }
      };
    }
  }

  @BeforeAll
  static void setUpSshServer() throws Exception {
    // Setup Virtual File System Root
    serverRoot = Files.createTempDirectory("sftp-root");
    Files.createFile(serverRoot.resolve("test1.txt"));
    Path subDir = Files.createDirectory(serverRoot.resolve("subdir"));
    Files.createFile(subDir.resolve("test2.txt"));

    // Generate Key Pair for Key Authentication
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();
    PublicKey expectedPublicKey = keyPair.getPublic();

    // Write Private Key to File
    privateKeyPath = Files.createTempFile("id_rsa", "");
    String privateKeyContent =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(keyPair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
    Files.writeString(privateKeyPath, privateKeyContent);

    // Setup SSH Server
    sshServer = SshServer.setUpDefaultServer();
    sshServer.setPort(0); // Random port
    sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());

    // Setup SFTP Subsystem
    SftpSubsystemFactory factory = new SftpSubsystemFactory.Builder().build();
    sshServer.setSubsystemFactories(Collections.singletonList(factory));

    // Setup Virtual File System
    VirtualFileSystemFactory vfsFactory = new VirtualFileSystemFactory(serverRoot.toAbsolutePath());
    sshServer.setFileSystemFactory(vfsFactory);

    // Setup Authentication
    sshServer.setPasswordAuthenticator(
        (username, password, session) ->
            "testuser".equals(username) && "testpass".equals(password));

    sshServer.setPublickeyAuthenticator(
        (username, key, session) -> "testuser".equals(username) && key.equals(expectedPublicKey));

    sshServer.start();
  }

  @AfterAll
  static void tearDownSshServer() throws IOException {
    if (sshServer != null) {
      sshServer.stop();
    }
    Files.walk(serverRoot)
        .sorted(java.util.Comparator.reverseOrder())
        .map(Path::toFile)
        .forEach(java.io.File::delete);
    Files.deleteIfExists(privateKeyPath);
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    // SFTP properties
    registry.add("sftp.servers[0].id", () -> "server-password");
    registry.add("sftp.servers[0].host", () -> "localhost");
    registry.add("sftp.servers[0].port", () -> sshServer.getPort());
    registry.add("sftp.servers[0].username", () -> "testuser");
    registry.add("sftp.servers[0].password", () -> "testpass");
    registry.add("sftp.servers[0].authType", () -> SftpProperties.AuthType.PASSWORD);

    registry.add("sftp.servers[1].id", () -> "server-key");
    registry.add("sftp.servers[1].host", () -> "localhost");
    registry.add("sftp.servers[1].port", () -> sshServer.getPort());
    registry.add("sftp.servers[1].username", () -> "testuser");
    registry.add(
        "sftp.servers[1].privateKeyPath", () -> privateKeyPath.toAbsolutePath().toString());
    registry.add("sftp.servers[1].authType", () -> SftpProperties.AuthType.KEY);

    registry.add("sftp.servers[2].id", () -> "server-both");
    registry.add("sftp.servers[2].host", () -> "localhost");
    registry.add("sftp.servers[2].port", () -> sshServer.getPort());
    registry.add("sftp.servers[2].username", () -> "testuser");
    registry.add("sftp.servers[2].password", () -> "testpass");
    registry.add(
        "sftp.servers[2].privateKeyPath", () -> privateKeyPath.toAbsolutePath().toString());
    registry.add("sftp.servers[2].authType", () -> SftpProperties.AuthType.PASSWORD_AND_KEY);
  }

  @Test
  void testScanningForAllAuthTypes() throws Exception {
    // Clear database before test
    jdbcTemplate.execute("DELETE FROM sftp_file_registry");

    // Act
    JobContext mockJobContext = mock(JobContext.class);
    JobDashboardLogger mockDashboardLogger = mock(JobDashboardLogger.class);
    when(mockJobContext.logger()).thenReturn(mockDashboardLogger);

    sftpScanningJob.performWork(mockJobContext);

    // Assert
    // We expect 2 files per server * 3 servers = 6 records in the database
    Integer count =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sftp_file_registry", Integer.class);
    assertEquals(6, count, "Should have scanned 2 files from 3 configured servers");

    // Verify specific server records
    List<String> serverIds =
        jdbcTemplate.queryForList(
            "SELECT DISTINCT server_id FROM sftp_file_registry", String.class);
    assertTrue(serverIds.contains("server-password"));
    assertTrue(serverIds.contains("server-key"));
    assertTrue(serverIds.contains("server-both"));
  }
}
