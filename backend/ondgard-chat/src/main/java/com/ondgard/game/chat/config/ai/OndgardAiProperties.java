package com.ondgard.game.chat.config.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties( prefix = "ondgard.ai" )
public class OndgardAiProperties {

  private GeminiProperties gemini;
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
    private Integer numCtx;
    private Double topP;
    private Integer topK;
  }

  @Data
  public static class GeminiProperties {
    private String apiKey;
    private String gmModel;
    private String summaryModel = "gemini-3-flash-preview";
    private String embeddingModel = "gemini-embedding-001";
    private int embeddingDimensions = 3072;
    private int embeddingTpmLimit = 25_000;
    private String inventoryModel = "gemini-3-flash-preview";
    private String inventoryReviewerModel = "gemini-3-flash-preview";
    private String questModel = "gemini-3-flash-preview";
    private String sceneModel = "gemini-2.5-flash-lite";
    private String validatorModel = "gemini-3-flash-preview";
    private String ragQueryModel = "gemini-2.5-flash";
    private String advisorModel = "gemini-3-flash-preview";
    private String setupAdvisorModel = "gemini-3-flash-preview";
    private String setupSummaryModel = "gemini-2.5-flash";
    private String setupGeneratorModel = "gemini-3-flash-preview";
    private String nameRaceSetupModel = "gemini-2.5-flash";
    private GeminiOptionsProperties options;
  }

  @Data
  public static class GeminiOptionsProperties {
    private Double temperature;
    private Integer maxOutputTokens;
    private Double topP;
  }
}
