package org.pk.collector.integration.sftp;

import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.SFTPException;
import org.pk.collector.core.model.SftpFileStatus;
import org.pk.collector.core.model.SftpFileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

@Service
public class RecursiveSftpScanner {

  private static final Logger log = LoggerFactory.getLogger(RecursiveSftpScanner.class);
  private static final int BATCH_SIZE = 1000;

  /* Injects the scanning stream into the Consumer every thousand files without destroying memory */
  public void scanAll(
      SFTPClient client,
      String rootPath,
      String serverId,
      Consumer<List<SftpFileRecord>> batchProcessor)
      throws IOException {
    List<SftpFileRecord> buffer = new ArrayList<>();
    traverseDirectory(client, rootPath, serverId, buffer, batchProcessor);

    /* Dumping the final remnants that did not exceed the buffer size */
    if (!buffer.isEmpty()) {
      batchProcessor.accept(buffer);
    }
  }

  private void traverseDirectory(
      SFTPClient client,
      String currentPath,
      String serverId,
      List<SftpFileRecord> buffer,
      Consumer<List<SftpFileRecord>> batchProcessor)
      throws IOException {

    List<RemoteResourceInfo> entries;
    try {
      entries = client.ls(currentPath);
    } catch (SFTPException e) {
      log.warn("Failed to list directory contents for path: {} on server: {}. Reason: {}", currentPath, serverId, e.getMessage());
      return;
    }

    for (RemoteResourceInfo entry : entries) {
      String filename = entry.getName();
      if (filename.equals(".") || filename.equals("..")) {
        continue;
      }

      String fullPath = entry.getPath();
      FileAttributes attrs = entry.getAttributes();

      if (attrs.getType() == FileMode.Type.DIRECTORY) {
        traverseDirectory(client, fullPath, serverId, buffer, batchProcessor);
      } else if (attrs.getType() == FileMode.Type.REGULAR) {
        buffer.add(buildRecord(serverId, fullPath, attrs));

        if (buffer.size() >= BATCH_SIZE) {
          batchProcessor.accept(new ArrayList<>(buffer));
          buffer.clear();
        }
      }
    }
  }

  private SftpFileRecord buildRecord(
      String serverId, String filePath, FileAttributes attrs) {
    SftpFileRecord record = new SftpFileRecord();
    record.setId(generateDeterministicId(serverId, filePath));
    record.setStatus(SftpFileStatus.NEW);
    record.setServerId(serverId);
    record.setFilePath(filePath);
    record.setFileSize(attrs.getSize());
    record.setCreatedAt(Instant.ofEpochSecond(attrs.getMtime()));
    record.setModifiedAt(Instant.ofEpochSecond(attrs.getMtime()));
    record.setLastSeenAt(LocalDate.now());
    return record;
  }

  private String generateDeterministicId(String serverId, String filePath) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String payload = serverId + "::" + filePath;
      byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Java cryptographic error.", e);
    }
  }
}
