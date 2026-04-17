package org.pk.collector.core.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pk.collector.core.model.SftpFileRecord;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SftpFileBatchRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private SftpFileBatchRepository repository;

    @Test
    void bulkUpsert_shouldExecuteBatchUpdateWithCorrectParameters() {
        // Arrange
        Instant now = Instant.now();
        SftpFileRecord record1 = createTestRecord("id1", "server1", "file1.txt", "/path/file1.txt", 1024L, now);
        SftpFileRecord record2 = createTestRecord("id2", "server2", "file2.txt", "/path/file2.txt", 2048L, now);
        List<SftpFileRecord> records = List.of(record1, record2);

        // Act
        repository.bulkUpsert(records);

        // Assert
        verify(jdbcTemplate).batchUpdate(anyString(), eq(records), eq(1000), any());
    }

    @Test
    void bulkUpsert_whenRecordsHaveNullTimestamps_shouldHandleNulls() {
        // Arrange
        SftpFileRecord record = createTestRecord("id1", "server1", "file1.txt", "/path/file1.txt", 1024L, null);
        record.setCreationTimestamp(null);
        record.setModificationTimestamp(null);
        List<SftpFileRecord> records = List.of(record);

        // Act
        repository.bulkUpsert(records);

        // Assert
        verify(jdbcTemplate).batchUpdate(anyString(), eq(records), eq(1000), any());
    }

    @Test
    void bulkUpsert_whenEmptyList_shouldExecuteBatchUpdate() {
        // Arrange
        List<SftpFileRecord> records = List.of();

        // Act
        repository.bulkUpsert(records);

        // Assert
        verify(jdbcTemplate).batchUpdate(anyString(), eq(records), eq(1000), any());
    }

    @Test
    void bulkUpsert_shouldUseCorrectBatchSize() {
        // Arrange
        List<SftpFileRecord> records = List.of(
            createTestRecord("id1", "server1", "file1.txt", "/path/file1.txt", 1024L, Instant.now())
        );

        // Act
        repository.bulkUpsert(records);

        // Assert
        verify(jdbcTemplate).batchUpdate(anyString(), eq(records), eq(1000), any());
    }

    @Test
    void bulkUpsert_whenBatchUpdateThrowsException_shouldPropagate() {
        // Arrange
        List<SftpFileRecord> records = List.of(
            createTestRecord("id1", "server1", "file1.txt", "/path/file1.txt", 1024L, Instant.now())
        );
        doThrow(new RuntimeException("Database error")).when(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            repository.bulkUpsert(records));
        assertEquals("Database error", exception.getMessage());
    }

    private SftpFileRecord createTestRecord(String id, String serverId, String fileName, String filePath, Long fileSize, Instant timestamp) {
        SftpFileRecord record = new SftpFileRecord();
        record.setId(id);
        record.setServerId(serverId);
        record.setFileName(fileName);
        record.setFilePath(filePath);
        record.setFileSize(fileSize);
        record.setCreationTimestamp(timestamp);
        record.setModificationTimestamp(timestamp);
        record.setLastScannedAt(Instant.now());
        return record;
    }
}
