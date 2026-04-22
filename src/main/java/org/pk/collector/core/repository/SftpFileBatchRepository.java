package org.pk.collector.core.repository;

import lombok.RequiredArgsConstructor;
import org.pk.collector.core.model.SftpFileStatus;
import org.pk.collector.core.model.SftpFileRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SftpFileBatchRepository {

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public void bulkUpsert(List<SftpFileRecord> records) {
    String sql =
        """
                INSERT INTO sftp_file_registry
                (id, status, server_id, file_path, file_size, created_at, modified_at, last_seen_at)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO UPDATE SET
                    last_seen_at = CASE
                        WHEN EXCLUDED.last_seen_at > sftp_file_registry.last_seen_at THEN EXCLUDED.last_seen_at
                        ELSE sftp_file_registry.last_seen_at
                    END,
                    modified_at = EXCLUDED.modified_at,
                    file_size = EXCLUDED.file_size,
                    status = CASE
                        WHEN sftp_file_registry.file_size IS DISTINCT FROM EXCLUDED.file_size OR sftp_file_registry.modified_at IS DISTINCT FROM EXCLUDED.modified_at THEN 'NEW'
                        ELSE sftp_file_registry.status
                    END
                WHERE sftp_file_registry.status IN ('NEW', 'REJECTED')
                """;

    jdbcTemplate.batchUpdate(
        sql,
        records,
        1000,
        (ps, record) -> {
          ps.setString(1, record.getId());
          ps.setString(
              2,
              record.getStatus() != null ? record.getStatus().name() : SftpFileStatus.NEW.name());
          ps.setString(3, record.getServerId());
          ps.setString(4, record.getFilePath());
          ps.setObject(5, record.getFileSize(), Types.BIGINT);
          ps.setTimestamp(
              6, record.getCreatedAt() != null ? Timestamp.from(record.getCreatedAt()) : null);
          ps.setTimestamp(
              7, record.getModifiedAt() != null ? Timestamp.from(record.getModifiedAt()) : null);
          ps.setDate(8, Date.valueOf(record.getLastSeenAt()));
        });
  }
}
