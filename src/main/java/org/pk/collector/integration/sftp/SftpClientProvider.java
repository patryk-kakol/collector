package org.pk.collector.integration.sftp;

import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.pk.collector.config.SftpProperties.ServerConfig;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class SftpClientProvider {

  public void executeWithClient(ServerConfig config, SftpCallback callback) throws Exception {
    try (SSHClient client = new SSHClient()) {
      client.addHostKeyVerifier(new PromiscuousVerifier());

      log.info("Establishing session for node: {}", config.id());

      if (config.proxy() != null) {
        log.info(
                "Routing through SOCKS5 proxy {}:{}", config.proxy().host(), config.proxy().port());
        client.setSocketFactory(
                new Socks5SocketFactory(config.proxy().host(), config.proxy().port()));
      }

      client.connect(config.host(), config.port());

      configureAuthentication(client, config);
      log.info("Authentication successful for node: {}", config.id());

      try (SFTPClient sftpClient = client.newSFTPClient()) {
        callback.doWithSftpClient(sftpClient, config);
      }
    }
  }

  private void configureAuthentication(SSHClient client, ServerConfig config) throws Exception {
    switch (config.authType()) {
      case PASSWORD -> client.authPassword(config.username(), config.password());
      case KEY -> client.authPublickey(config.username(), config.privateKeyPath());
      case PASSWORD_AND_KEY -> {
        client.authPublickey(config.username(), config.privateKeyPath());
        if (!client.isAuthenticated()) {
          client.authPassword(config.username(), config.password());
        }
      }
    }
  }

  @FunctionalInterface
  public interface SftpCallback {
    void doWithSftpClient(SFTPClient client, ServerConfig config) throws Exception;
  }
}
