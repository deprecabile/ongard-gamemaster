package com.ongard.game.warmup.client;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaHealthClientTest {

  private OllamaHealthClient client;
  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void setUp() throws IOException {
    client = new OllamaHealthClient();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    baseUrl = "http://localhost:" + server.getAddress().getPort();
  }

  @AfterEach
  void tearDown() {
    if( server != null ){
      server.stop(0);
    }
  }

  // --- isReady ---

  @Test
  void isReady_returnsTrue_whenServerResponds200() {
    server.createContext("/api/tags", exchange -> {
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    server.start();

    assertThat(client.isReady(baseUrl)).isTrue();
  }

  @Test
  void isReady_returnsFalse_whenServerResponds500() {
    server.createContext("/api/tags", exchange -> {
      exchange.sendResponseHeaders(500, -1);
      exchange.close();
    });
    server.start();

    assertThat(client.isReady(baseUrl)).isFalse();
  }

  @Test
  void isReady_returnsFalse_whenServerIsUnreachable() {
    // server not started — port bound but not listening won't work,
    // use a port that's definitely not listening
    assertThat(client.isReady("http://localhost:1")).isFalse();
  }

  // --- warmUpGenerate ---

  @Test
  void warmUpGenerate_succeeds_whenServerResponds200WithBody() {
    server.createContext("/api/generate", exchange -> {
      byte[] response = "{\"response\":\"Ciao\"}".getBytes();
      exchange.sendResponseHeaders(200, response.length);
      try( OutputStream os = exchange.getResponseBody() ){
        os.write(response);
      }
    });
    server.start();

    assertThatNoException().isThrownBy(() ->
        client.warmUpGenerate(baseUrl, "test-model", 10));
  }

  @Test
  void warmUpGenerate_throwsModelNotFoundException_on404() {
    server.createContext("/api/generate", exchange -> {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
    });
    server.start();

    assertThatThrownBy(() -> client.warmUpGenerate(baseUrl, "missing-model", 10))
        .isInstanceOf(OllamaHealthClient.ModelNotFoundException.class)
        .hasMessageContaining("missing-model")
        .hasMessageContaining("404");
  }

  @Test
  void warmUpGenerate_throwsWarmupException_on500() {
    server.createContext("/api/generate", exchange -> {
      exchange.sendResponseHeaders(500, -1);
      exchange.close();
    });
    server.start();

    assertThatThrownBy(() -> client.warmUpGenerate(baseUrl, "test-model", 10))
        .isInstanceOf(OllamaHealthClient.WarmupException.class)
        .isNotInstanceOf(OllamaHealthClient.ModelNotFoundException.class)
        .hasMessageContaining("HTTP 500");
  }

  @Test
  void warmUpGenerate_throwsWarmupException_onEmptyBody() {
    server.createContext("/api/generate", exchange -> {
      exchange.sendResponseHeaders(200, 0);
      exchange.getResponseBody().close();
    });
    server.start();

    assertThatThrownBy(() -> client.warmUpGenerate(baseUrl, "test-model", 10))
        .isInstanceOf(OllamaHealthClient.WarmupException.class)
        .hasMessageContaining("test-model");
  }

  // --- warmUpEmbed ---

  @Test
  void warmUpEmbed_succeeds_whenServerResponds200WithBody() {
    server.createContext("/api/embed", exchange -> {
      byte[] response = "{\"embeddings\":[[0.1,0.2]]}".getBytes();
      exchange.sendResponseHeaders(200, response.length);
      try( OutputStream os = exchange.getResponseBody() ){
        os.write(response);
      }
    });
    server.start();

    assertThatNoException().isThrownBy(() ->
        client.warmUpEmbed(baseUrl, "embed-model", 10));
  }

  @Test
  void warmUpEmbed_throwsModelNotFoundException_on404() {
    server.createContext("/api/embed", exchange -> {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
    });
    server.start();

    assertThatThrownBy(() -> client.warmUpEmbed(baseUrl, "missing-embed", 10))
        .isInstanceOf(OllamaHealthClient.ModelNotFoundException.class)
        .hasMessageContaining("missing-embed");
  }

  @Test
  void warmUpEmbed_throwsWarmupException_on500() {
    server.createContext("/api/embed", exchange -> {
      exchange.sendResponseHeaders(500, -1);
      exchange.close();
    });
    server.start();

    assertThatThrownBy(() -> client.warmUpEmbed(baseUrl, "embed-model", 10))
        .isInstanceOf(OllamaHealthClient.WarmupException.class)
        .isNotInstanceOf(OllamaHealthClient.ModelNotFoundException.class);
  }
}
