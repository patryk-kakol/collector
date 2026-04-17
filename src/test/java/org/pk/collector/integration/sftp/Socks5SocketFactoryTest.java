package org.pk.collector.integration.sftp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

class Socks5SocketFactoryTest {

    @Test
    void createSocket_shouldReturnSocketWithSocksProxy() {
        // Arrange
        String proxyHost = "127.0.0.1";
        int proxyPort = 1080;
        Socks5SocketFactory factory = new Socks5SocketFactory(proxyHost, proxyPort);

        // Act
        Socket socket = factory.createSocket();

        // Assert
        assertNotNull(socket);
        // It's hard to verify internal proxy of a Socket without reflection, 
        // but we can assert the socket is created and not connected yet.
        assertFalse(socket.isConnected());
    }

    @Test
    void createSocket_withHostAndPort_shouldThrowUnsupportedOperationException() {
        Socks5SocketFactory factory = new Socks5SocketFactory("127.0.0.1", 1080);
        assertThrows(UnsupportedOperationException.class, () -> factory.createSocket("localhost", 22));
    }

    @Test
    void createSocket_withInetAddress_shouldThrowUnsupportedOperationException() {
        Socks5SocketFactory factory = new Socks5SocketFactory("127.0.0.1", 1080);
        assertThrows(UnsupportedOperationException.class, () -> factory.createSocket(java.net.InetAddress.getByName("localhost"), 22));
    }

    @Test
    void createSocket_withHostPortAndLocalAddress_shouldThrowUnsupportedOperationException() {
        Socks5SocketFactory factory = new Socks5SocketFactory("127.0.0.1", 1080);
        assertThrows(UnsupportedOperationException.class, () -> factory.createSocket("localhost", 22, java.net.InetAddress.getByName("localhost"), 0));
    }

    @Test
    void createSocket_withInetAddressPortAndLocalAddress_shouldThrowUnsupportedOperationException() {
        Socks5SocketFactory factory = new Socks5SocketFactory("127.0.0.1", 1080);
        assertThrows(UnsupportedOperationException.class, () -> 
            factory.createSocket(java.net.InetAddress.getByName("localhost"), 22, java.net.InetAddress.getByName("localhost"), 0));
    }

    @Test
    void customSocket_connect_shouldDeferDnsResolutionToProxy() {
        // Arrange: use a dummy proxy port that is closed to simulate a network attempt
        Socks5SocketFactory factory = new Socks5SocketFactory("127.0.0.1", 20000);
        Socket socket = factory.createSocket();

        // Act & Assert
        // We use a non-existent hostname. If local DNS resolution wasn't bypassed via
        // InetSocketAddress.createUnresolved(), this would throw UnknownHostException.
        // Because it is bypassed, it will attempt to connect to the proxy and fail with ConnectException or SocketException.
        IOException exception = assertThrows(IOException.class, () -> 
            socket.connect(new InetSocketAddress("non.existent.domain.internal", 22), 1000));

        assertFalse(exception instanceof UnknownHostException, "Expected proxy connection failure, but got local DNS resolution failure");
    }
}
