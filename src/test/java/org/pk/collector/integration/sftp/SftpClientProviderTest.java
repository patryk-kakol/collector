package org.pk.collector.integration.sftp;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pk.collector.config.SftpProperties;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SftpClientProviderTest {

  @Mock private SFTPClient sftpClient;

  @InjectMocks private SftpClientProvider sftpClientProvider;

  @Test
  void executeWithClient_whenPasswordAuth_shouldAuthenticateWithPassword() throws Exception {
    // Arrange
    SftpProperties.ServerConfig config =
        new SftpProperties.ServerConfig(
            "node1",
            "localhost",
            22,
            "user",
            SftpProperties.AuthType.PASSWORD,
            "password",
            null,
            null);
    SftpClientProvider.SftpCallback callback = mock(SftpClientProvider.SftpCallback.class);

    try (MockedConstruction<SSHClient> mockedSSH =
        mockConstruction(
            SSHClient.class,
            (mock, _) -> when(mock.newSFTPClient()).thenReturn(sftpClient))) {

      // Act
      sftpClientProvider.executeWithClient(config, callback);

      // Assert
      SSHClient sshClientMock = mockedSSH.constructed().getFirst();
      verify(sshClientMock).connect("localhost", 22);
      verify(sshClientMock).authPassword("user", "password");
      verify(sshClientMock).newSFTPClient();
      verify(callback).doWithSftpClient(sftpClient, config);
      verify(sftpClient).close();
      verify(sshClientMock).close();
    }
  }

  @Test
  void executeWithClient_whenKeyAuth_shouldAuthenticateWithKey() throws Exception {
    // Arrange
    SftpProperties.ServerConfig config =
        new SftpProperties.ServerConfig(
            "node1",
            "localhost",
            22,
            "user",
            SftpProperties.AuthType.KEY,
            null,
            "/path/to/key",
            null);
    SftpClientProvider.SftpCallback callback = mock(SftpClientProvider.SftpCallback.class);

    try (MockedConstruction<SSHClient> mockedSSH =
        mockConstruction(
            SSHClient.class,
            (mock, _) -> when(mock.newSFTPClient()).thenReturn(sftpClient))) {

      // Act
      sftpClientProvider.executeWithClient(config, callback);

      // Assert
      SSHClient sshClientMock = mockedSSH.constructed().getFirst();
      verify(sshClientMock).authPublickey("user", "/path/to/key");
      verify(callback).doWithSftpClient(sftpClient, config);
    }
  }

  @Test
  void executeWithClient_whenPasswordAndKeyAuth_shouldTryBoth() throws Exception {
    // Arrange
    SftpProperties.ServerConfig config =
        new SftpProperties.ServerConfig(
            "node1",
            "localhost",
            22,
            "user",
            SftpProperties.AuthType.PASSWORD_AND_KEY,
            "password",
            "/path/to/key",
            null);
    SftpClientProvider.SftpCallback callback = mock(SftpClientProvider.SftpCallback.class);

    try (MockedConstruction<SSHClient> mockedSSH =
        mockConstruction(
            SSHClient.class,
            (mock, _) -> {
              when(mock.newSFTPClient()).thenReturn(sftpClient);
              when(mock.isAuthenticated()).thenReturn(false); // First auth fails
            })) {

      // Act
      sftpClientProvider.executeWithClient(config, callback);

      // Assert
      SSHClient sshClientMock = mockedSSH.constructed().getFirst();
      verify(sshClientMock).authPublickey("user", "/path/to/key");
      verify(sshClientMock).authPassword("user", "password");
      verify(callback).doWithSftpClient(sftpClient, config);
    }
  }

  @Test
  void executeWithClient_whenPasswordAndKeyAuthAndKeySucceeds_shouldNotTryPassword()
      throws Exception {
    // Arrange
    SftpProperties.ServerConfig config =
        new SftpProperties.ServerConfig(
            "node1",
            "localhost",
            22,
            "user",
            SftpProperties.AuthType.PASSWORD_AND_KEY,
            "password",
            "/path/to/key",
            null);
    SftpClientProvider.SftpCallback callback = mock(SftpClientProvider.SftpCallback.class);

    try (MockedConstruction<SSHClient> mockedSSH =
        mockConstruction(
            SSHClient.class,
            (mock, _) -> {
              when(mock.newSFTPClient()).thenReturn(sftpClient);
              when(mock.isAuthenticated()).thenReturn(true); // First auth succeeds
            })) {

      // Act
      sftpClientProvider.executeWithClient(config, callback);

      // Assert
      SSHClient sshClientMock = mockedSSH.constructed().getFirst();
      verify(sshClientMock).authPublickey("user", "/path/to/key");
      verify(sshClientMock, never()).authPassword(anyString(), anyString());
      verify(callback).doWithSftpClient(sftpClient, config);
    }
  }

  @Test
  void executeWithClient_whenProxyConfigured_shouldSetSocketFactory() throws Exception {
    // Arrange
    var proxy = new SftpProperties.ProxyConfig("proxy.example.com", 1080);
    SftpProperties.ServerConfig config =
        new SftpProperties.ServerConfig(
            "node1",
            "localhost",
            22,
            "user",
            SftpProperties.AuthType.PASSWORD,
            "password",
            null,
            proxy);
    SftpClientProvider.SftpCallback callback = mock(SftpClientProvider.SftpCallback.class);

    try (MockedConstruction<SSHClient> mockedSSH =
        mockConstruction(
            SSHClient.class,
            (mock, _) -> when(mock.newSFTPClient()).thenReturn(sftpClient))) {

      // Act
      sftpClientProvider.executeWithClient(config, callback);

      // Assert
      SSHClient sshClientMock = mockedSSH.constructed().getFirst();
      verify(sshClientMock).setSocketFactory(any(Socks5SocketFactory.class));
      verify(callback).doWithSftpClient(sftpClient, config);
    }
  }

  @Test
  void executeWithClient_whenConnectionFails_shouldPropagateException() throws Exception {
    // Arrange
    SftpProperties.ServerConfig config =
        new SftpProperties.ServerConfig(
            "node1",
            "localhost",
            22,
            "user",
            SftpProperties.AuthType.PASSWORD,
            "password",
            null,
            null);
    SftpClientProvider.SftpCallback callback = mock(SftpClientProvider.SftpCallback.class);

    try (MockedConstruction<SSHClient> mockedSSH =
        mockConstruction(
            SSHClient.class,
            (mock, _) ->
                doThrow(new IOException("Connection failed"))
                    .when(mock)
                    .connect(anyString(), anyInt()))) {

      // Act & Assert
      IOException exception =
          assertThrows(
              IOException.class, () -> sftpClientProvider.executeWithClient(config, callback));
      assertEquals("Connection failed", exception.getMessage());

      SSHClient sshClientMock = mockedSSH.constructed().getFirst();
      verify(sshClientMock).close();
    }
  }

  @Test
  void executeWithClient_whenCallbackThrowsException_shouldStillCloseResources() throws Exception {
    // Arrange
    SftpProperties.ServerConfig config =
        new SftpProperties.ServerConfig(
            "node1",
            "localhost",
            22,
            "user",
            SftpProperties.AuthType.PASSWORD,
            "password",
            null,
            null);
    SftpClientProvider.SftpCallback callback = mock(SftpClientProvider.SftpCallback.class);
    doThrow(new RuntimeException("Callback failed")).when(callback).doWithSftpClient(any(), any());

    try (MockedConstruction<SSHClient> mockedSSH =
        mockConstruction(
            SSHClient.class,
            (mock, _) -> when(mock.newSFTPClient()).thenReturn(sftpClient))) {

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class, () -> sftpClientProvider.executeWithClient(config, callback));
      assertEquals("Callback failed", exception.getMessage());
      verify(sftpClient).close();

      SSHClient sshClientMock = mockedSSH.constructed().getFirst();
      verify(sshClientMock).close();
    }
  }

  @Test
  void executeWithClient_whenAuthTypeIsNull_shouldThrowNullPointerException() {
    // Arrange
    SftpProperties.ServerConfig config =
        new SftpProperties.ServerConfig(
            "node1",
            "localhost",
            22,
            "user",
            null, // This hits the implicit null-check branch of the switch statement
            "password",
            null,
            null);
    SftpClientProvider.SftpCallback callback = mock(SftpClientProvider.SftpCallback.class);

    try (MockedConstruction<SSHClient> _ =
        mockConstruction(SSHClient.class)) {

      // Act & Assert
      assertThrows(NullPointerException.class, () -> sftpClientProvider.executeWithClient(config, callback));
    }
  }
}
