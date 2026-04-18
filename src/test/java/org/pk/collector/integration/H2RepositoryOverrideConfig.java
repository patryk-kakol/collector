package org.pk.collector.integration;

import org.pk.collector.core.model.SftpFileRecord;
import org.pk.collector.core.repository.SftpFileBatchRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

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
        String sql =
"""
            MERGE INTO sftp_file_registry (
                id,
                server_id,
                file_name,
                file_path,
                file_size,
                creation_timestamp,
                modification_timestamp,
                last_scanned_at
                ) KEY (id)
            VALUES (?,?,?,?,?,?,?,?)
""";

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
