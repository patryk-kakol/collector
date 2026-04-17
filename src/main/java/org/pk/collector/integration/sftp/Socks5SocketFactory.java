package org.pk.collector.integration.sftp;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;

public class Socks5SocketFactory extends SocketFactory {

  private final String proxyHost;
  private final int proxyPort;

  public Socks5SocketFactory(String proxyHost, int proxyPort) {
    this.proxyHost = proxyHost;
    this.proxyPort = proxyPort;
  }

  @Override
  public Socket createSocket() {
    Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort));

    return new Socket(proxy) {
      @Override
      public void connect(SocketAddress endpoint, int timeout) throws IOException {
        // Block local DNS resolution and force the SOCKS proxy to resolve the target hostname
        InetSocketAddress inetEndpoint = (InetSocketAddress) endpoint;
        super.connect(
            InetSocketAddress.createUnresolved(inetEndpoint.getHostName(), inetEndpoint.getPort()),
            timeout);
      }
    };
  }

  // SSHJ only uses the no-args createSocket() internally.
  // These must be overridden to satisfy the abstract class but are safely ignored.
  @Override
  public Socket createSocket(String host, int port) {
    throw new UnsupportedOperationException("This method is not supported");
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
    throw new UnsupportedOperationException("This method is not supported");
  }

  @Override
  public Socket createSocket(InetAddress host, int port) {
    throw new UnsupportedOperationException("This method is not supported");
  }

  @Override
  public Socket createSocket(
      InetAddress address, int port, InetAddress localAddress, int localPort) {
    throw new UnsupportedOperationException("This method is not supported");
  }
}
