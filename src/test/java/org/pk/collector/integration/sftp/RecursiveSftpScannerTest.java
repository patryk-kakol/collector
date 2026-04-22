package org.pk.collector.integration.sftp;

import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.SFTPException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pk.collector.core.model.SftpFileRecord;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecursiveSftpScannerTest {

    @Mock
    private SFTPClient sftpClient;

    @Mock
    private Consumer<List<SftpFileRecord>> batchConsumer;

    @Captor
    private ArgumentCaptor<List<SftpFileRecord>> batchCaptor;

    private RecursiveSftpScanner recursiveSftpScanner;

    @BeforeEach
    void setUp() {
        recursiveSftpScanner = new RecursiveSftpScanner();
    }

    private RemoteResourceInfo mockFile(String name, String path, long size, long mtime) {
        RemoteResourceInfo file = mock(RemoteResourceInfo.class);
        when(file.getName()).thenReturn(name);
        when(file.getPath()).thenReturn(path);
        FileAttributes attributes = new FileAttributes.Builder()
                .withType(FileMode.Type.REGULAR)
                .withSize(size)
                .withAtimeMtime((int) mtime, (int) mtime)
                .build();
        when(file.getAttributes()).thenReturn(attributes);
        return file;
    }

    private RemoteResourceInfo mockDirectory(String name, String path) {
        RemoteResourceInfo dir = mock(RemoteResourceInfo.class);
        when(dir.getName()).thenReturn(name);
        when(dir.getPath()).thenReturn(path);
        FileAttributes attributes = new FileAttributes.Builder().withType(FileMode.Type.DIRECTORY).build();
        when(dir.getAttributes()).thenReturn(attributes);
        return dir;
    }

    @Test
    void scanAll_shouldRecursivelyScanAndBatchResults() throws IOException {
        // Arrange
        String nodeId = "test-node";
        String path = "/";

        RemoteResourceInfo file1 = mockFile("file1.txt", "/file1.txt", 1024, Instant.now().getEpochSecond());
        RemoteResourceInfo dir1 = mockDirectory("dir1", "/dir1");
        RemoteResourceInfo file2 = mockFile("file2.txt", "/dir1/file2.txt", 2048, Instant.now().getEpochSecond());

        when(sftpClient.ls(path)).thenReturn(List.of(file1, dir1));
        when(sftpClient.ls("/dir1")).thenReturn(List.of(file2));

        // Act
        recursiveSftpScanner.scanAll(sftpClient, path, nodeId, batchConsumer);

        // Assert
        verify(batchConsumer).accept(batchCaptor.capture());
        List<SftpFileRecord> capturedBatch = batchCaptor.getValue();
        assertEquals(2, capturedBatch.size());
        assertEquals("file1.txt", capturedBatch.get(0).getFileName());
        assertEquals("file2.txt", capturedBatch.get(1).getFileName());
    }

    @Test
    void scanAll_whenLargeBatch_shouldSplitIntoMultipleBatches() throws IOException {
        // Arrange
        String nodeId = "test-node";
        String path = "/";
        int totalFiles = 1500;

        List<RemoteResourceInfo> files = new ArrayList<>();
        for (int i = 0; i < totalFiles; i++) {
            files.add(mockFile("file" + i + ".txt", "/file" + i + ".txt", 100, Instant.now().getEpochSecond()));
        }

        when(sftpClient.ls(path)).thenReturn(files);

        // Act
        recursiveSftpScanner.scanAll(sftpClient, path, nodeId, batchConsumer);

        // Assert
        verify(batchConsumer, times(2)).accept(batchCaptor.capture());
        List<List<SftpFileRecord>> allBatches = batchCaptor.getAllValues();
        assertEquals(1000, allBatches.get(0).size());
        assertEquals(500, allBatches.get(1).size());
    }

    @Test
    void scanAll_whenEmptyDirectory_shouldNotCallBatchConsumer() throws IOException {
        // Arrange
        String nodeId = "test-node";
        String path = "/";
        when(sftpClient.ls(path)).thenReturn(List.of());

        // Act
        recursiveSftpScanner.scanAll(sftpClient, path, nodeId, batchConsumer);

        // Assert
        verify(batchConsumer, never()).accept(anyList());
    }

    @Test
    void scanAll_shouldSkipDotAndDotDotEntries() throws IOException {
        // Arrange
        String nodeId = "test-node";
        String path = "/";

        RemoteResourceInfo dot = mock(RemoteResourceInfo.class);
        when(dot.getName()).thenReturn(".");
        RemoteResourceInfo dotDot = mock(RemoteResourceInfo.class);
        when(dotDot.getName()).thenReturn("..");
        RemoteResourceInfo file = mockFile("file.txt", "/file.txt", 123, Instant.now().getEpochSecond());

        when(sftpClient.ls(path)).thenReturn(List.of(dot, dotDot, file));

        // Act
        recursiveSftpScanner.scanAll(sftpClient, path, nodeId, batchConsumer);

        // Assert
        verify(batchConsumer).accept(batchCaptor.capture());
        assertEquals(1, batchCaptor.getValue().size());
        assertEquals("file.txt", batchCaptor.getValue().getFirst().getFileName());
    }

    @Test
    void scanAll_whenLsThrowsSFTPException_shouldContinueScanningOtherDirectories() throws IOException {
        // Arrange
        String nodeId = "test-node";
        String rootPath = "/";

        RemoteResourceInfo goodDir = mockDirectory("good-dir", "/good-dir");
        RemoteResourceInfo badDir = mockDirectory("bad-dir", "/bad-dir");
        
        RemoteResourceInfo goodFile = mockFile("good-file.txt", "/good-dir/good-file.txt", 123, Instant.now().getEpochSecond());

        when(sftpClient.ls(rootPath)).thenReturn(List.of(goodDir, badDir));
        when(sftpClient.ls("/good-dir")).thenReturn(List.of(goodFile));
        when(sftpClient.ls("/bad-dir")).thenThrow(new SFTPException("Permission denied"));

        // Act
        recursiveSftpScanner.scanAll(sftpClient, rootPath, nodeId, batchConsumer);

        // Assert
        verify(batchConsumer).accept(batchCaptor.capture());
        List<SftpFileRecord> capturedBatch = batchCaptor.getValue();
        assertEquals(1, capturedBatch.size());
        assertEquals("good-file.txt", capturedBatch.getFirst().getFileName());
        
        verify(sftpClient).ls("/bad-dir"); // verify it was attempted
    }

    @Test
    void scanAll_whenLsThrowsNonSFTPException_shouldPropagate() throws IOException {
        // Arrange
        String nodeId = "test-node";
        String rootPath = "/";

        when(sftpClient.ls(rootPath)).thenThrow(new IOException("Network error"));

        // Act & Assert
        IOException exception = assertThrows(IOException.class, () ->
                recursiveSftpScanner.scanAll(sftpClient, rootPath, nodeId, batchConsumer));
        assertEquals("Network error", exception.getMessage());
        verify(batchConsumer, never()).accept(anyList());
    }

    @Test
    void scanAll_withOtherFileTypes_shouldOnlyProcessRegularFiles() throws IOException {
        // Arrange
        String nodeId = "test-node";
        String path = "/";

        RemoteResourceInfo regularFile = mockFile("file.txt", "/file.txt", 1024, Instant.now().getEpochSecond());
        RemoteResourceInfo symlink = mock(RemoteResourceInfo.class);
        when(symlink.getName()).thenReturn("link");
        when(symlink.getAttributes()).thenReturn(new FileAttributes.Builder().withType(FileMode.Type.SYMLINK).build());

        when(sftpClient.ls(path)).thenReturn(List.of(regularFile, symlink));

        // Act
        recursiveSftpScanner.scanAll(sftpClient, path, nodeId, batchConsumer);

        // Assert
        verify(batchConsumer).accept(batchCaptor.capture());
        assertEquals(1, batchCaptor.getValue().size());
        assertEquals("file.txt", batchCaptor.getValue().getFirst().getFileName());
    }
}
