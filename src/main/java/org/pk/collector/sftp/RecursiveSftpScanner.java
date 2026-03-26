package org.pk.collector.sftp;

import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClient.Attributes;
import org.apache.sshd.sftp.client.SftpClient.DirEntry;
import org.pk.collector.domain.SftpFileRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

@Service
public class RecursiveSftpScanner {

    private static final int BATCH_SIZE = 1000;

    /**
     * Injects the scanning stream into the Consumer every thousand files without destroying memory.
     */
    public void scanAll(SftpClient client, String rootPath, String serverId, Consumer<List<SftpFileRecord>> batchProcessor) throws IOException {
        List<SftpFileRecord> buffer = new ArrayList<>();
        traverseDirectory(client, rootPath, serverId, buffer, batchProcessor);

        // Dumping the so-called final remnants that did not exceed the buffer size
        if (!buffer.isEmpty()) {
            batchProcessor.accept(buffer);
        }
    }

    private void traverseDirectory(SftpClient client, String currentPath, String serverId,
                                   List<SftpFileRecord> buffer, Consumer<List<SftpFileRecord>> batchProcessor) throws IOException {
        Iterable<DirEntry> entries = client.readDir(currentPath);

        for (DirEntry entry : entries) {
            String filename = entry.getFilename();
            if (filename.equals(".") || filename.equals("..")) {
                continue;
            }

            String fullPath = currentPath.endsWith("/")? currentPath + filename : currentPath + "/" + filename;
            Attributes attrs = entry.getAttributes();

            if (attrs.isDirectory()) {
                traverseDirectory(client, fullPath, serverId, buffer, batchProcessor);
            } else if (attrs.isRegularFile()) {
                buffer.add(buildRecord(serverId, filename, fullPath, attrs));

                if (buffer.size() >= BATCH_SIZE) {
                    batchProcessor.accept(new ArrayList<>(buffer));
                    buffer.clear();
                }
            }
        }
    }

    private SftpFileRecord buildRecord(String serverId, String fileName, String filePath, Attributes attrs) {
        SftpFileRecord record = new SftpFileRecord();
        record.setId(generateDeterministicId(serverId, filePath));
        record.setServerId(serverId);
        record.setFileName(fileName);
        record.setFilePath(filePath);
        record.setFileSize(attrs.getSize());
        record.setCreationTimestamp(attrs.getCreateTime()!= null? attrs.getCreateTime().toInstant() : null);
        record.setModificationTimestamp(attrs.getModifyTime()!= null? attrs.getModifyTime().toInstant() : null);
        record.setLastScannedAt(Instant.now());
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
