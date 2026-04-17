package org.pk.collector.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SftpPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void constructor_withValidServers_shouldCreateProperties() {
        // Arrange
        var proxy = new SftpProperties.ProxyConfig("proxy.example.com", 1080);
        var server = new SftpProperties.ServerConfig(
            "test-server",
            "sftp.example.com",
            22,
            "user",
            SftpProperties.AuthType.PASSWORD,
            "password",
            null,
            proxy
        );

        // Act
        var properties = new SftpProperties(List.of(server));

        // Assert
        assertNotNull(properties);
        assertEquals(1, properties.servers().size());
        assertEquals("test-server", properties.servers().getFirst().id());
        assertTrue(validator.validate(server).isEmpty());
    }

    @Test
    void serverConfig_withPasswordAuth_shouldHaveValidFields() {
        // Arrange & Act
        var server = new SftpProperties.ServerConfig(
            "test-server",
            "sftp.example.com",
            22,
            "user",
            SftpProperties.AuthType.PASSWORD,
            "password",
            null,
            null
        );

        // Assert
        assertEquals("test-server", server.id());
        assertEquals("sftp.example.com", server.host());
        assertEquals(22, server.port());
        assertEquals("user", server.username());
        assertEquals(SftpProperties.AuthType.PASSWORD, server.authType());
        assertEquals("password", server.password());
        assertNull(server.privateKeyPath());
        assertNull(server.proxy());
        assertTrue(validator.validate(server).isEmpty());
    }

    @Test
    void serverConfig_withKeyAuth_shouldHaveValidFields() {
        // Arrange & Act
        var server = new SftpProperties.ServerConfig(
            "test-server",
            "sftp.example.com",
            22,
            "user",
            SftpProperties.AuthType.KEY,
            null,
            "/path/to/key",
            null
        );

        // Assert
        assertEquals(SftpProperties.AuthType.KEY, server.authType());
        assertNull(server.password());
        assertEquals("/path/to/key", server.privateKeyPath());
        assertTrue(validator.validate(server).isEmpty());
    }

    @Test
    void serverConfig_withPasswordAndKeyAuth_shouldHaveValidFields() {
        // Arrange & Act
        var server = new SftpProperties.ServerConfig(
            "test-server",
            "sftp.example.com",
            22,
            "user",
            SftpProperties.AuthType.PASSWORD_AND_KEY,
            "password",
            "/path/to/key",
            null
        );

        // Assert
        assertEquals(SftpProperties.AuthType.PASSWORD_AND_KEY, server.authType());
        assertEquals("password", server.password());
        assertEquals("/path/to/key", server.privateKeyPath());
        assertTrue(validator.validate(server).isEmpty());
    }

    @Test
    void proxyConfig_shouldStoreHostAndPort() {
        // Arrange & Act
        var proxy = new SftpProperties.ProxyConfig("proxy.example.com", 1080);

        // Assert
        assertEquals("proxy.example.com", proxy.host());
        assertEquals(1080, proxy.port());
    }

    @Test
    void authType_shouldHaveAllExpectedValues() {
        // Assert
        assertEquals(3, SftpProperties.AuthType.values().length);
        assertTrue(List.of(SftpProperties.AuthType.values()).contains(SftpProperties.AuthType.PASSWORD));
        assertTrue(List.of(SftpProperties.AuthType.values()).contains(SftpProperties.AuthType.KEY));
        assertTrue(List.of(SftpProperties.AuthType.values()).contains(SftpProperties.AuthType.PASSWORD_AND_KEY));
    }

    @Test
    void constructor_withEmptyServers_shouldCreateEmptyProperties() {
        // Arrange & Act
        var properties = new SftpProperties(Collections.emptyList());

        // Assert
        assertNotNull(properties);
        assertTrue(properties.servers().isEmpty());
    }

    @Test
    void serverConfig_withNullId_shouldFailValidation() {
        // Arrange
        var server = new SftpProperties.ServerConfig(
            null, "sftp.example.com", 22, "user", SftpProperties.AuthType.PASSWORD, "password", null, null
        );

        // Act
        var violations = validator.validate(server);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "id".equals(v.getPropertyPath().toString())));
    }

    @Test
    void serverConfig_withNullHost_shouldFailValidation() {
        // Arrange
        var server = new SftpProperties.ServerConfig(
            "test-server", null, 22, "user", SftpProperties.AuthType.PASSWORD, "password", null, null
        );

        // Act
        var violations = validator.validate(server);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "host".equals(v.getPropertyPath().toString())));
    }

    @Test
    void serverConfig_withNullUsername_shouldFailValidation() {
        // Arrange
        var server = new SftpProperties.ServerConfig(
            "test-server", "sftp.example.com", 22, null, SftpProperties.AuthType.PASSWORD, "password", null, null
        );

        // Act
        var violations = validator.validate(server);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "username".equals(v.getPropertyPath().toString())));
    }

    @Test
    void serverConfig_withNullPort_shouldFailValidation() {
        // Arrange
        var server = new SftpProperties.ServerConfig(
            "test-server", "sftp.example.com", null, "user", SftpProperties.AuthType.PASSWORD, "password", null, null
        );

        // Act
        var violations = validator.validate(server);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "port".equals(v.getPropertyPath().toString())));
    }

    @Test
    void serverConfig_withNullAuthType_shouldFailValidation() {
        // Arrange
        var server = new SftpProperties.ServerConfig(
            "test-server", "sftp.example.com", 22, "user", null, "password", null, null
        );

        // Act
        var violations = validator.validate(server);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "authType".equals(v.getPropertyPath().toString())));
    }
}