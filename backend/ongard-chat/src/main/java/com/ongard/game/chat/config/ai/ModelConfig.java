package com.ongard.game.chat.config.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * UNICO punto nel codebase dove compare {@link OllamaChatModel}.
 * Tutto il resto del codice (GM, Router, Revisori, Orchestratore)
 * inietta esclusivamente l'interfaccia {@link ChatModel}.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties( OndgardAiProperties.class )
public class ModelConfig {

  private final OndgardAiProperties props;

  /**
   * Game Master model — creativo, gira su ollama-gpu.
   * Temperature alta, sampling ampio per narrazione ricca.
   */
  @Bean
  @Qualifier( "gmModel" )
  public ChatModel gmModel() {
    var gpu = props.getOllamaGpu();
    var opts = gpu.getOptions();

    log.info("Initializing gmModel: model={}, url={}", gpu.getModel(), gpu.getBaseUrl());

    var api = OllamaApi.builder()
        .baseUrl(gpu.getBaseUrl())
        .build();

    return OllamaChatModel.builder()
        .ollamaApi(api)
        .defaultOptions(OllamaChatOptions.builder()
            .model(gpu.getModel())
            .temperature(opts.getTemperature())
            .numPredict(opts.getNumPredict())
            .topP(opts.getTopP())
            .build())
        .build();
  }

  /**
   * Light model — deterministico, gira su ollama-cpu.
   * Temperature 0, greedy decoding per Router, Revisori e Agenti Inventario.
   */
  @Bean
  @Qualifier( "lightModel" )
  public ChatModel lightModel() {
    var cpu = props.getOllamaCpu();
    var opts = cpu.getOptions();

    log.info("Initializing lightModel: model={}, url={}", cpu.getModel(), cpu.getBaseUrl());

    var optionsBuilder = OllamaChatOptions.builder()
        .model(cpu.getModel())
        .temperature(opts.getTemperature())
        .numPredict(opts.getNumPredict())
        .topP(opts.getTopP());

    if( opts.getTopK() != null ){
      optionsBuilder.topK(opts.getTopK());
    }

    var api = OllamaApi.builder()
        .baseUrl(cpu.getBaseUrl())
        .build();

    return OllamaChatModel.builder()
        .ollamaApi(api)
        .defaultOptions(optionsBuilder.build())
        .build();
  }

  /**
   * Embedding model — qwen3-embedding, gira su ollama-cpu.
   * Usato da RagInitializationService (ETL) e LoreRetrievalService (query).
   */
  @Bean
  public EmbeddingModel embeddingModel() {
    var cpu = props.getOllamaCpu();

    log.info("Initializing embeddingModel: model={}, url={}", cpu.getEmbeddingModel(), cpu.getBaseUrl());

    var api = OllamaApi.builder()
        .baseUrl(cpu.getBaseUrl())
        .build();

    return OllamaEmbeddingModel.builder()
        .ollamaApi(api)
        .defaultOptions(OllamaEmbeddingOptions.builder()
            .model(cpu.getEmbeddingModel())
            .build())
        .build();
  }
}
