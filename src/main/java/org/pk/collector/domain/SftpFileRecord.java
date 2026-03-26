package org.pk.collector.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "sftp_file_registry")
@Getter
@Setter
public class SftpFileRecord {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "server_id", nullable = false)
    private String serverId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 2048)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "creation_timestamp")
    private Instant creationTimestamp;

    @Column(name = "modification_timestamp")
    private Instant modificationTimestamp;

    @Column(name = "last_scanned_at", nullable = false)
    private Instant lastScannedAt;
}