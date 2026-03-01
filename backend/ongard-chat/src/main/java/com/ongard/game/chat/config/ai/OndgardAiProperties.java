package com.ongard.game.chat.config.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties( prefix = "ondgard.ai" )
public class OndgardAiProperties {

  private OllamaInstanceProperties ollamaGpu;
  private OllamaInstanceProperties ollamaCpu;

  @Data
  public static class OllamaInstanceProperties {
    private String baseUrl;
    private String model;
    private String embeddingModel;
    private OllamaOptionsProperties options;
  }

  @Data
  public static class OllamaOptionsProperties {
    private Double temperature;
    private Integer numPredict;
    private Double topP;
    private Integer topK;
  }
}
