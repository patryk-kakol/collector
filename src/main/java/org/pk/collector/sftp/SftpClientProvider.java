package org.pk.collector.sftp;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.pk.collector.config.SftpProperties.ServerConfig;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.time.Duration;

@Slf4j
@Component
public class SftpClientProvider {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration AUTH_TIMEOUT = Duration.ofSeconds(15);

    public void executeWithClient(ServerConfig config, SftpCallback callback) throws Exception {
        try (SshClient client = SshClient.setUpDefaultClient()) {
            client.start();
            log.info("Establishing protected connection with host: {}", config.id());

            try (ClientSession session = client.connect(config.username(), config.host(), config.port())
                    .verify(CONNECT_TIMEOUT).getSession()) {

                configureAuthentication(session, config);
                session.auth().verify(AUTH_TIMEOUT);

                try (SftpClient sftpClient = SftpClientFactory.instance().createSftpClient(session)) {
                    callback.doWithSftpClient(sftpClient, config);
                }
            }
        }
    }

    private void configureAuthentication(ClientSession session, ServerConfig config) {
        switch (config.authType()) {
            case PASSWORD -> session.addPasswordIdentity(config.password());
            case KEY -> loadKeyIdentity(session, config.privateKeyPath());
            case PASSWORD_AND_KEY -> {
                loadKeyIdentity(session, config.privateKeyPath());
                session.addPasswordIdentity(config.password());
            }
        }
    }

    private void loadKeyIdentity(ClientSession session, String keyPath) {
        if (keyPath == null || keyPath.isBlank()) {
            throw new IllegalArgumentException("Key parameter is unavailable.");
        }
        session.setKeyIdentityProvider(new FileKeyPairProvider(Paths.get(keyPath)));
    }

    @FunctionalInterface
    public interface SftpCallback {
        void doWithSftpClient(SftpClient client, ServerConfig config) throws Exception;
    }
}
