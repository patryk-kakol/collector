package org.pk.collector.repository;


import lombok.RequiredArgsConstructor;
import org.pk.collector.domain.SftpFileRecord;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SftpFileBatchRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Reduces I/O time significantly. Uses true bulk inserts instead of Data JPA N+1 layer.
     */
    @Transactional
    public void bulkUpsert(List<SftpFileRecord> records) {
        String sql = """
                INSERT INTO sftp_file_registry
                (id, server_id, file_name, file_path, file_size, creation_timestamp, modification_timestamp, last_scanned_at)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO UPDATE SET
                    last_scanned_at = EXCLUDED.last_scanned_at,
                    modification_timestamp = EXCLUDED.modification_timestamp,
                    file_size = EXCLUDED.file_size
                """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                SftpFileRecord record = records.get(i);
                ps.setString(1, record.getId());
                ps.setString(2, record.getServerId());
                ps.setString(3, record.getFileName());
                ps.setString(4, record.getFilePath());

                if (record.getFileSize() != null) {
                    ps.setLong(5, record.getFileSize());
                } else {
                    ps.setNull(5, java.sql.Types.BIGINT);
                }

                ps.setTimestamp(6, record.getCreationTimestamp() != null ? Timestamp.from(record.getCreationTimestamp()) : null);
                ps.setTimestamp(7, record.getModificationTimestamp() != null ? Timestamp.from(record.getModificationTimestamp()) : null);
                ps.setTimestamp(8, Timestamp.from(record.getLastScannedAt()));
            }

            @Override
            public int getBatchSize() {
                return records.size();
            }
        });
    }
}
