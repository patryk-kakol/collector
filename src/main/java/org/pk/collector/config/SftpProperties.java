package org.pk.collector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@ConfigurationProperties(prefix = "sftp")
@Validated
public record SftpProperties(List<ServerConfig> servers) {
  public record ServerConfig(
      @NotBlank String id,
      @NotBlank String host,
      @NotBlank int port,
      @NotBlank String username,
      @NotNull AuthType authType,
      String password,
      String privateKeyPath) {}

  public enum AuthType {
    PASSWORD,
    KEY,
    PASSWORD_AND_KEY
  }
}
