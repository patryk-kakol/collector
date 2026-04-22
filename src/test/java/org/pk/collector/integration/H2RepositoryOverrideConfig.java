package org.pk.collector.integration;

import org.pk.collector.core.model.SftpFileStatus;
import org.pk.collector.core.model.SftpFileRecord;
import org.pk.collector.core.repository.SftpFileBatchRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

/* Testing replacement for SftpFileBatchRepository using H2 MERGE INTO syntax,
since H2 doesn't support PostgreSQL's ON CONFLICT syntax. */
@TestConfiguration
public class H2RepositoryOverrideConfig {

  @Bean
  @Primary
  public SftpFileBatchRepository sftpFileBatchRepository(JdbcTemplate jdbcTemplate) {
    return new SftpFileBatchRepository(jdbcTemplate) {
      @Override
      public void bulkUpsert(List<SftpFileRecord> records) {
        String mergeSql =
            """
                MERGE INTO sftp_file_registry target
                USING (
                    SELECT cast(? as varchar) as id,
                           cast(? as varchar) as status,
                           cast(? as varchar) as server_id,
                           cast(? as varchar) as file_path,
                           cast(? as bigint) as file_size,
                           cast(? as timestamp) as created_at,
                           cast(? as timestamp) as modified_at,
                           cast(? as date) as last_seen_at
                    FROM DUAL
                ) source ON target.id = source.id
                WHEN MATCHED AND target.status IN ('NEW', 'REJECTED') THEN UPDATE SET
                    last_seen_at = CASE
                        WHEN source.last_seen_at > target.last_seen_at THEN source.last_seen_at
                        ELSE target.last_seen_at
                    END,
                    modified_at = source.modified_at,
                    file_size = source.file_size,
                    status = CASE
                        WHEN target.file_size IS DISTINCT FROM source.file_size OR target.modified_at IS DISTINCT FROM source.modified_at THEN 'NEW'
                        ELSE target.status
                    END
                WHEN NOT MATCHED THEN INSERT (
                    id, status, server_id, file_path, file_size, created_at, modified_at, last_seen_at
                ) VALUES (
                    source.id, source.status, source.server_id, source.file_path, source.file_size, source.created_at, source.modified_at, source.last_seen_at
                )
                """;

        jdbcTemplate.batchUpdate(
            mergeSql,
            records,
            1000,
            (ps, record) -> {
              ps.setString(1, record.getId());
              ps.setString(
                  2,
                  record.getStatus() != null
                      ? record.getStatus().name()
                      : SftpFileStatus.NEW.name());
              ps.setString(3, record.getServerId());
              ps.setString(4, record.getFilePath());
              ps.setObject(5, record.getFileSize(), Types.BIGINT);
              ps.setTimestamp(
                  6, record.getCreatedAt() != null ? Timestamp.from(record.getCreatedAt()) : null);
              ps.setTimestamp(
                  7,
                  record.getModifiedAt() != null ? Timestamp.from(record.getModifiedAt()) : null);
              ps.setDate(8, Date.valueOf(record.getLastSeenAt()));
            });
      }
    };
  }
}
