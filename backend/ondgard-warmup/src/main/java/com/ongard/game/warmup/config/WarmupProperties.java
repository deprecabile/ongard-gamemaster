package com.ongard.game.warmup.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties( prefix = "ondgard.warmup" )
public class WarmupProperties {

  private OllamaInstanceConfig ollamaGpu;
  private OllamaInstanceConfig ollamaCpu;
  private HealthCheckConfig healthCheck;
  private WarmupConfig warmup;

  @Data
  public static class OllamaInstanceConfig {
    private String baseUrl;
    private String model;
    private String embeddingModel;
  }

  @Data
  public static class HealthCheckConfig {
    private int maxRetries = 30;
    private long retryIntervalMs = 10_000;
  }

  @Data
  public static class WarmupConfig {
    private int timeoutSeconds = 300;
    private int maxRetries = 2;
  }
}
