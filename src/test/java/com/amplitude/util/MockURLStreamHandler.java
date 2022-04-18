package com.amplitude.util;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.when;

public class MockURLStreamHandler extends URLStreamHandler implements URLStreamHandlerFactory {

  private final Map<URL, URLConnection> connections = new HashMap<>();

  private static final MockURLStreamHandler instance = new MockURLStreamHandler();

  public static MockURLStreamHandler getInstance() {
    return instance;
  }

  @Override
  protected URLConnection openConnection(URL url) throws IOException {
    return openConnection(url, Proxy.NO_PROXY);
  }

  @Override
  protected URLConnection openConnection(URL u, Proxy p) throws IOException {
    URLConnection connection = connections.get(u);
    if (p.equals(Proxy.NO_PROXY)) {
      when(((HttpURLConnection) connection).usingProxy()).thenReturn(false);
    } else {
      when(((HttpURLConnection) connection).usingProxy()).thenReturn(true);
    }
    return connection;
  }

  public void resetConnections() {
    connections.clear();
  }

  public MockURLStreamHandler setConnection(URL url, URLConnection urlConnection) {
    connections.put(url, urlConnection);
    return this;
  }

  @Override
  public URLStreamHandler createURLStreamHandler(String protocol) {
    return getInstance();
  }
}
