package org.pk.collector.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SftpFileRecordTest {

    @Test
    void testNoArgsConstructorAndDefaultValues() {
        // Arrange & Act
        SftpFileRecord record = new SftpFileRecord();

        // Assert
        assertNull(record.getId());
        assertNull(record.getServerId());
        assertNull(record.getFileName());
        assertNull(record.getFilePath());
        assertNull(record.getFileSize());
        assertNull(record.getCreationTimestamp());
        assertNull(record.getModificationTimestamp());
        assertNull(record.getLastScannedAt());
    }

    @Test
    void testAllArgsConstructor() {
        // Arrange
        String id = "record-123";
        String serverId = "server-1";
        String fileName = "test.txt";
        String filePath = "/data/test.txt";
        Long fileSize = 1024L;
        Instant creationTimestamp = Instant.now().minusSeconds(3600);
        Instant modificationTimestamp = Instant.now().minusSeconds(1800);
        Instant lastScannedAt = Instant.now();

        // Act
        SftpFileRecord record = new SftpFileRecord(
                id,
                serverId,
                fileName,
                filePath,
                fileSize,
                creationTimestamp,
                modificationTimestamp,
                lastScannedAt
        );

        // Assert
        assertEquals(id, record.getId());
        assertEquals(serverId, record.getServerId());
        assertEquals(fileName, record.getFileName());
        assertEquals(filePath, record.getFilePath());
        assertEquals(fileSize, record.getFileSize());
        assertEquals(creationTimestamp, record.getCreationTimestamp());
        assertEquals(modificationTimestamp, record.getModificationTimestamp());
        assertEquals(lastScannedAt, record.getLastScannedAt());
    }

    @Test
    void testGettersAndSetters() {
        // Arrange
        SftpFileRecord record = new SftpFileRecord();
        
        String id = "record-456";
        String serverId = "server-2";
        String fileName = "data.csv";
        String filePath = "/data/data.csv";
        Long fileSize = 2048L;
        Instant creationTimestamp = Instant.now().minusSeconds(7200);
        Instant modificationTimestamp = Instant.now().minusSeconds(3600);
        Instant lastScannedAt = Instant.now();

        // Act
        record.setId(id);
        record.setServerId(serverId);
        record.setFileName(fileName);
        record.setFilePath(filePath);
        record.setFileSize(fileSize);
        record.setCreationTimestamp(creationTimestamp);
        record.setModificationTimestamp(modificationTimestamp);
        record.setLastScannedAt(lastScannedAt);

        // Assert
        assertEquals(id, record.getId());
        assertEquals(serverId, record.getServerId());
        assertEquals(fileName, record.getFileName());
        assertEquals(filePath, record.getFilePath());
        assertEquals(fileSize, record.getFileSize());
        assertEquals(creationTimestamp, record.getCreationTimestamp());
        assertEquals(modificationTimestamp, record.getModificationTimestamp());
        assertEquals(lastScannedAt, record.getLastScannedAt());
    }
}
