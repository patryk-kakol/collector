package org.pk.collector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@ConfigurationProperties(prefix = "sftp")
@Validated
public record SftpProperties(List<@Valid ServerConfig> servers) {
  public record ServerConfig(
      @NotBlank String id,
      @NotBlank String host,
      @NotNull Integer port,
      @NotBlank String username,
      @NotNull AuthType authType,
      String password,
      String privateKeyPath,
      ProxyConfig proxy) {}

  public record ProxyConfig(String host, Integer port) {}

  public enum AuthType {
    PASSWORD,
    KEY,
    PASSWORD_AND_KEY
  }
}
