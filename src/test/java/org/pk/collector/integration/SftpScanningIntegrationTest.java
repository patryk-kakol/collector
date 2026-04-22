package org.pk.collector.integration;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.context.JobDashboardLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pk.collector.config.SftpProperties;
import org.pk.collector.jobs.SftpScanningJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"resource", "ResultOfMethodCallIgnored", "SpringBootApplicationProperties"})
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
@Import(H2RepositoryOverrideConfig.class)
class SftpScanningIntegrationTest {

  private static SshServer sshServer;
  private static Path serverRoot;
  private static Path privateKeyPath;

  @Autowired private SftpScanningJob sftpScanningJob;

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private SftpProperties sftpProperties;

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

    // Set up SFTP Subsystem
    SftpSubsystemFactory factory = new SftpSubsystemFactory.Builder().build();
    sshServer.setSubsystemFactories(Collections.singletonList(factory));

    // Set up Virtual File System
    VirtualFileSystemFactory vfsFactory = new VirtualFileSystemFactory(serverRoot.toAbsolutePath());
    sshServer.setFileSystemFactory(vfsFactory);

    // Set up Authentication
    sshServer.setPasswordAuthenticator(
        (username, password, _) -> "testuser".equals(username) && "testpass".equals(password));

    sshServer.setPublickeyAuthenticator(
        (username, key, _) -> "testuser".equals(username) && key.equals(expectedPublicKey));

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

  @BeforeEach
  void setUp() {
    // Clear database before each test
    jdbcTemplate.execute("DELETE FROM sftp_file_registry WHERE true");
  }

  @Test
  void testScanningForAllAuthTypes_PositiveScenarios() throws Exception {
    // Arrange: Use only the good servers
    List<SftpProperties.ServerConfig> correctServers =
        List.of(
            new SftpProperties.ServerConfig(
                "server-password",
                "localhost",
                sshServer.getPort(),
                "testuser",
                SftpProperties.AuthType.PASSWORD,
                "testpass",
                null,
                null),
            new SftpProperties.ServerConfig(
                "server-key",
                "localhost",
                sshServer.getPort(),
                "testuser",
                SftpProperties.AuthType.KEY,
                null,
                privateKeyPath.toAbsolutePath().toString(),
                null),
            new SftpProperties.ServerConfig(
                "server-both",
                "localhost",
                sshServer.getPort(),
                "testuser",
                SftpProperties.AuthType.PASSWORD_AND_KEY,
                "testpass",
                privateKeyPath.toAbsolutePath().toString(),
                null));
    when(sftpProperties.servers()).thenReturn(correctServers);

    JobContext mockJobContext = mock(JobContext.class);
    JobDashboardLogger mockDashboardLogger = mock(JobDashboardLogger.class);
    when(mockJobContext.logger()).thenReturn(mockDashboardLogger);

    // Act
    sftpScanningJob.performWork(mockJobContext);

    // Assert
    // We expect 2 files per server * 3 servers = 6 records in the database
    Integer count =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sftp_file_registry", Integer.class);
    assertEquals(6, count, "Should have scanned 2 files from 3 configured servers");

    List<String> serverIds =
        jdbcTemplate.queryForList(
            "SELECT DISTINCT server_id FROM sftp_file_registry", String.class);
    assertTrue(serverIds.contains("server-password"));
    assertTrue(serverIds.contains("server-key"));
    assertTrue(serverIds.contains("server-both"));

    // Check if initial status is NEW
    List<String> statuses =
        jdbcTemplate.queryForList("SELECT DISTINCT status FROM sftp_file_registry", String.class);
    assertEquals(1, statuses.size());
    assertEquals("NEW", statuses.getFirst());
  }

  @Test
  void testStatusUpdateWhenFileChanged() throws Exception {
    // Arrange
    List<SftpProperties.ServerConfig> correctServers =
        List.of(
            new SftpProperties.ServerConfig(
                "server-password",
                "localhost",
                sshServer.getPort(),
                "testuser",
                SftpProperties.AuthType.PASSWORD,
                "testpass",
                null,
                null));
    when(sftpProperties.servers()).thenReturn(correctServers);

    JobContext mockJobContext = mock(JobContext.class);
    JobDashboardLogger mockDashboardLogger = mock(JobDashboardLogger.class);
    when(mockJobContext.logger()).thenReturn(mockDashboardLogger);

    // First scan
    sftpScanningJob.performWork(mockJobContext);

    // Verify initial state
    List<Map<String, Object>> records =
        jdbcTemplate.queryForList("SELECT * FROM sftp_file_registry WHERE file_path LIKE '%test1.txt'");
    assertEquals(1, records.size());
    assertEquals("NEW", records.getFirst().get("status"));

    // Change status to ACCEPTED
    jdbcTemplate.update(
        "UPDATE sftp_file_registry SET status = 'ACCEPTED' WHERE file_path LIKE '%test1.txt'");

    // Modify file
    Files.writeString(serverRoot.resolve("test1.txt"), "some content", StandardOpenOption.APPEND);

    // Second scan (file has changed, but status is ACCEPTED)
    sftpScanningJob.performWork(mockJobContext);

    // Verify status has NOT changed from ACCEPTED
    String status =
        jdbcTemplate.queryForObject(
            "SELECT status FROM sftp_file_registry WHERE file_path LIKE '%test1.txt'", String.class);
    assertEquals("ACCEPTED", status, "Status should not change from ACCEPTED even if file is modified");

    // Now, let's test the REJECTED case
    // Change status to REJECTED
    jdbcTemplate.update(
        "UPDATE sftp_file_registry SET status = 'REJECTED' WHERE file_path LIKE '%test1.txt'");

    // Sanity check
    status =
        jdbcTemplate.queryForObject(
            "SELECT status FROM sftp_file_registry WHERE file_path LIKE '%test1.txt'", String.class);
    assertEquals("REJECTED", status);

    // Modify file again to ensure it's different
    Files.writeString(serverRoot.resolve("test1.txt"), "some more content", StandardOpenOption.APPEND);

    // Third scan
    sftpScanningJob.performWork(mockJobContext);

    // Verify status has changed back to NEW from REJECTED
    status =
        jdbcTemplate.queryForObject(
            "SELECT status FROM sftp_file_registry WHERE file_path LIKE '%test1.txt'", String.class);
    assertEquals("NEW", status, "Status should change from REJECTED to NEW if file is modified");


    // Cleanup modification
    Files.writeString(serverRoot.resolve("test1.txt"), "", StandardOpenOption.TRUNCATE_EXISTING);
  }

  @Test
  void testScanning_whenWrongPassword_shouldThrowException() {
    // Arrange: Configure only a single bad server
    List<SftpProperties.ServerConfig> incorrectServers =
        List.of(
            new SftpProperties.ServerConfig(
                "server-wrong-password",
                "localhost",
                sshServer.getPort(),
                "testuser",
                SftpProperties.AuthType.PASSWORD,
                "wrongpass",
                null,
                null));
    when(sftpProperties.servers()).thenReturn(incorrectServers);

    JobContext mockJobContext = mock(JobContext.class);
    JobDashboardLogger mockDashboardLogger = mock(JobDashboardLogger.class);
    when(mockJobContext.logger()).thenReturn(mockDashboardLogger);

    // Act & Assert
    Exception exception =
        assertThrows(Exception.class, () -> sftpScanningJob.performWork(mockJobContext));
    assertTrue(exception.getMessage().contains("Thread execution error"));

    // Database should be empty
    Integer count =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sftp_file_registry", Integer.class);
    assertEquals(0, count);
  }

  @Test
  void testScanning_whenServerUnavailable_shouldThrowException() {
    // Arrange: Configure only a single bad server pointing to a dead port
    List<SftpProperties.ServerConfig> incorrectServers =
        List.of(
            new SftpProperties.ServerConfig(
                "server-unavailable",
                "localhost",
                65535,
                "testuser",
                SftpProperties.AuthType.PASSWORD,
                "testpass",
                null,
                null));
    when(sftpProperties.servers()).thenReturn(incorrectServers);

    JobContext mockJobContext = mock(JobContext.class);
    JobDashboardLogger mockDashboardLogger = mock(JobDashboardLogger.class);
    when(mockJobContext.logger()).thenReturn(mockDashboardLogger);

    // Act & Assert
    Exception exception =
        assertThrows(Exception.class, () -> sftpScanningJob.performWork(mockJobContext));
    assertTrue(exception.getMessage().contains("Thread execution error"));

    // Database should be empty
    Integer count =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sftp_file_registry", Integer.class);
    assertEquals(0, count);
  }
}
