package org.pk.collector.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SftpFileRecordTest {

  @Test
  void testNoArgsConstructorAndDefaultValues() {
    // Arrange & Act
    SftpFileRecord record = new SftpFileRecord();

    // Assert
    assertNull(record.getId());
    assertEquals(SftpFileStatus.NEW, record.getStatus());
    assertNull(record.getServerId());
    assertNull(record.getFileName());
    assertNull(record.getFilePath());
    assertNull(record.getFileSize());
    assertNull(record.getCreatedAt());
    assertNull(record.getModifiedAt());
    assertNull(record.getLastSeenAt());
  }

  @Test
  void testAllArgsConstructor() {
    // Arrange
    String id = "record-123";
    SftpFileStatus status = SftpFileStatus.NEW;
    String serverId = "server-1";
    String filePath = "/data/test.txt";
    Long fileSize = 1024L;
    Instant createdAt = Instant.now().minusSeconds(3600);
    Instant modifiedAt = Instant.now().minusSeconds(1800);
    LocalDate lastScannedAt = LocalDate.now();

    // Act
    SftpFileRecord record =
        new SftpFileRecord(
            id,
            status,
            serverId,
            filePath,
            fileSize,
            createdAt,
            modifiedAt,
            lastScannedAt);

    // Assert
    assertEquals(id, record.getId());
    assertEquals(status, record.getStatus());
    assertEquals(serverId, record.getServerId());
    assertEquals("test.txt", record.getFileName());
    assertEquals(filePath, record.getFilePath());
    assertEquals(fileSize, record.getFileSize());
    assertEquals(createdAt, record.getCreatedAt());
    assertEquals(modifiedAt, record.getModifiedAt());
    assertEquals(lastScannedAt, record.getLastSeenAt());
  }

  @Test
  void testGettersAndSetters() {
    // Arrange
    SftpFileRecord record = new SftpFileRecord();

    String id = "record-456";
    SftpFileStatus status = SftpFileStatus.ACCEPTED;
    String serverId = "server-2";
    String filePath = "/data/data.csv";
    Long fileSize = 2048L;
    Instant createdAt = Instant.now().minusSeconds(7200);
    Instant modifiedAt = Instant.now().minusSeconds(3600);
    LocalDate lastSeenAt = LocalDate.now();

    // Act
    record.setId(id);
    record.setStatus(status);
    record.setServerId(serverId);
    record.setFilePath(filePath);
    record.setFileSize(fileSize);
    record.setCreatedAt(createdAt);
    record.setModifiedAt(modifiedAt);
    record.setLastSeenAt(lastSeenAt);

    // Assert
    assertEquals(id, record.getId());
    assertEquals(status, record.getStatus());
    assertEquals(serverId, record.getServerId());
    assertEquals("data.csv", record.getFileName());
    assertEquals(filePath, record.getFilePath());
    assertEquals(fileSize, record.getFileSize());
    assertEquals(createdAt, record.getCreatedAt());
    assertEquals(modifiedAt, record.getModifiedAt());
    assertEquals(lastSeenAt, record.getLastSeenAt());
  }
}
