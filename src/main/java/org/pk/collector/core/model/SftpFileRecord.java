package org.pk.collector.core.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "sftp_file_registry")
public class SftpFileRecord {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private String id;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private SftpFileStatus status = SftpFileStatus.NEW;

  @Column(name = "server_id", nullable = false, length = 30)
  private String serverId;

  @Column(name = "file_path", nullable = false, length = 512)
  private String filePath;

  @Column(name = "file_size")
  private Long fileSize;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "modified_at")
  private Instant modifiedAt;

  @Column(name = "last_seen_at", nullable = false)
  private LocalDate lastSeenAt;

  public String getFileName() {
    if (filePath == null) {
      return null;
    }
    int lastSlashIndex = filePath.lastIndexOf('/');
    if (lastSlashIndex >= 0) {
      return filePath.substring(lastSlashIndex + 1);
    }
    return filePath;
  }
}
