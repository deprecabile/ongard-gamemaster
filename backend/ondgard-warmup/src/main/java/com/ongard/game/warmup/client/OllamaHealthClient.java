package com.ongard.game.warmup.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OllamaHealthClient {

  private final HttpClient httpClient;

  public OllamaHealthClient() {
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  public boolean isReady(String baseUrl) {
    try{
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/api/tags"))
          .GET()
          .timeout(Duration.ofSeconds(10))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200;
    }catch(Exception e){
      log.debug("Health check failed for {}: {}", baseUrl, e.getMessage());
      return false;
    }
  }

  public void warmUpGenerate(String baseUrl, String model, int timeoutSeconds) throws WarmupException {
    String body = """
        {"model":"%s","prompt":"Ciao","stream":false,"options":{"num_predict":1}}""".formatted(model);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/api/generate"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .timeout(Duration.ofSeconds(timeoutSeconds))
        .build();

    sendWarmupRequest(request, baseUrl, model);
  }

  public void warmUpEmbed(String baseUrl, String model, int timeoutSeconds) throws WarmupException {
    String body = """
        {"model":"%s","input":"test"}""".formatted(model);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/api/embed"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .timeout(Duration.ofSeconds(timeoutSeconds))
        .build();

    sendWarmupRequest(request, baseUrl, model);
  }

  private void sendWarmupRequest(HttpRequest request, String baseUrl, String model) throws WarmupException {
    try{
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if( response.statusCode() == 404 ){
        throw new ModelNotFoundException(
            "Model '%s' not found on %s (HTTP 404)".formatted(model, baseUrl));
      }

      if( response.statusCode() != 200 || response.body() == null || response.body().isEmpty() ){
        throw new WarmupException(
            "Warmup failed for model '%s' on %s: HTTP %d"
                .formatted(model, baseUrl, response.statusCode()));
      }
    }catch(ModelNotFoundException e){
      throw e;
    }catch(WarmupException e){
      throw e;
    }catch(Exception e){
      throw new WarmupException(
          "Warmup request failed for model '%s' on %s: %s"
              .formatted(model, baseUrl, e.getMessage()), e);
    }
  }

  public static class WarmupException extends Exception {
    public WarmupException(String message) {
      super(message);
    }

    public WarmupException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class ModelNotFoundException extends WarmupException {
    public ModelNotFoundException(String message) {
      super(message);
    }
  }
}
