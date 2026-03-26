package org.pk.collector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.util.List;

@ConfigurationProperties(prefix = "sftp")
@Validated
public record SftpProperties(
        @Min(1) int scanIntervalMinutes,
        List<ServerConfig> servers
) {
    public record ServerConfig(
            @NotBlank String id,
            @NotBlank String host,
            int port,
            @NotBlank String username,
            @NotNull AuthType authType,
            String password,
            String privateKeyPath
    ) {
        public ServerConfig {
            if (port == 0) port = 22; // Default port value
        }
    }

    public enum AuthType {
        PASSWORD, KEY, PASSWORD_AND_KEY
    }
}
