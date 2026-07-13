package com.ondgard.game.chat.config.ai;

import com.google.genai.Client;
import com.ondgard.game.chat.client.AccountClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.TaskType;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * UNICO punto nel codebase dove compare i model provider concreti.
 * Tutto il resto del codice (GM, Router, Revisori, Orchestratore)
 * inietta esclusivamente l'interfaccia {@link ChatModel}.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties( OndgardAiProperties.class )
public class LlmModelConfig {

  private static final Duration LLM_READ_TIMEOUT = Duration.ofMinutes(5);

  private final OndgardAiProperties props;
  private final AccountClient accountClient;

  /**
   * Game Master model — creativo, gira su Google GenAI (Gemini 3 Flash).
   * Temperature alta, sampling ampio per narrazione ricca.
   * 1M token di contesto per contenere tutto il lore.
   */
  @Bean
  @Qualifier( "gmModel" )
  public ChatModel gmModel() {
    var gemini = props.getGemini();
    var opts = gemini.getOptions();

    log.info("Initializing gmModel: model={}, provider=Google GenAI", gemini.getGmModel());

    var model = GoogleGenAiChatModel.builder()
        .genAiClient(geminiClient(gemini))
        .defaultOptions(GoogleGenAiChatOptions.builder()
            .model(gemini.getGmModel())
            .temperature(opts.getTemperature())
            .maxOutputTokens(opts.getMaxOutputTokens())
            .topP(opts.getTopP()).build())
        .build();

    return new TokenAwareChatModelDecorator(model, "gmModel", accountClient);
  }

  /**
   * Summary model — Gemini con temperature 0 per SummaryAgent.
   * Output deterministico per riassunti narrativi coerenti.
   */
  @Bean
  @Qualifier( "summaryModel" )
  public ChatModel summaryModel() {
    var gemini = props.getGemini();

    log.info("Initializing summaryModel: model={}, provider=Google GenAI (temp=0)", gemini.getSummaryModel());

    var model = GoogleGenAiChatModel.builder()
        .genAiClient(geminiClient(gemini))
        .defaultOptions(GoogleGenAiChatOptions.builder()
            .model(gemini.getSummaryModel())
            .temperature(0.0)
            .maxOutputTokens(32768)
            .topP(1.0).build())
        .build();

    return new TokenAwareChatModelDecorator(model, "summaryModel", accountClient);
  }

  /**
   * Inventory model — Gemini dedicato per InventoryUpdater/Initializer.
   * Temperature 0, JSON output enforced, budget alto per inventari complessi.
   */
  @Bean
  @Qualifier( "inventoryModel" )
  public ChatModel inventoryModel() {
    var gemini = props.getGemini();

    log.info("Initializing inventoryModel: model={}, provider=Google GenAI (temp=0, JSON schema)", gemini.getInventoryModel());

    var model = GoogleGenAiChatModel.builder()
        .genAiClient(geminiClient(gemini))
        .defaultOptions(GoogleGenAiChatOptions.builder()
            .model(gemini.getInventoryModel())
            .temperature(0.0)
            .maxOutputTokens(131072)
            .topP(1.0)
            .responseMimeType("application/json")
            .responseSchema(LlmResponseSchema.INVENTORY_SCHEMA)
            .build())
        .build();

    return new TokenAwareChatModelDecorator(model, "inventoryModel", accountClient);
  }

  /**
   * Inventory reviewer model — Gemini dedicato per InventoryReviewer.
   * Temperature 0, JSON output con schema nativo per constrained decoding.
   */
  @Bean
  @Qualifier( "inventoryReviewerModel" )
  public ChatModel inventoryReviewerModel() {
    var gemini = props.getGemini();

    log.info("Initializing inventoryReviewerModel: model={}, provider=Google GenAI (temp=0, JSON schema)",
        gemini.getInventoryReviewerModel());

    var model = GoogleGenAiChatModel.builder()
        .genAiClient(geminiClient(gemini))
        .defaultOptions(GoogleGenAiChatOptions.builder()
            .model(gemini.getInventoryReviewerModel())
            .temperature(0.0)
            .maxOutputTokens(32768)
            .topP(1.0)
            .responseMimeType("application/json")
            .responseSchema(LlmResponseSchema.INVENTORY_REVIEWER_SCHEMA)
            .build())
        .build();

    return new TokenAwareChatModelDecorator(model, "inventoryReviewerModel", accountClient);
  }

  /**
   * Quest model — Gemini dedicato per QuestlogUpdater/Initializer.
   * Temperature 0, JSON output enforced.
   */
  @Bean
  @Qualifier( "questModel" )
  public ChatModel questModel() {
    var gemini = props.getGemini();

    log.info("Initializing questModel: model={}, provider=Google GenAI (temp=0, JSON schema)", gemini.getQuestModel());

    var model = GoogleGenAiChatModel.builder()
        .genAiClient(geminiClient(gemini))
        .defaultOptions(GoogleGenAiChatOptions.builder()
            .model(gemini.getQuestModel())
            .temperature(0.0)
            .maxOutputTokens(32768)
            .topP(1.0)
            .responseMimeType("application/json")
            .responseSchema(LlmResponseSchema.QUESTLOG_SCHEMA)
            .build())
        .build();

    return new TokenAwareChatModelDecorator(model, "questModel", accountClient);
  }

  /**
   * Scene model — Gemini Flash leggero per agenti scena (initializer/updater).
   * Temperature bassa (0.15) per output quasi-deterministico ma con minima variabilità.
   */
  @Bean
  @Qualifier( "sceneModel" )
  public ChatModel sceneModel() {
    var gemini = props.getGemini();

    log.info("Initializing sceneModel: model={}, provider=Google GenAI (temp=0.15, JSON schema)", gemini.getSceneModel());

    var model = GoogleGenAiChatModel.builder()
        .genAiClient(geminiClient(gemini))
        .defaultOptions(GoogleGenAiChatOptions.builder()
            .model(gemini.getSceneModel())
            .temperature(0.15)
            .maxOutputTokens(8192)
            .topP(1.0)
            .responseMimeType("application/json")
            .responseSchema(LlmResponseSchema.SCENE_SCHEMA)
            .build())
        .build();

    return new TokenAwareChatModelDecorator(model, "sceneModel", accountClient);
  }

  /**
   * Validator model — Gemini per lore reviewers. Temperature 0, JSON output con schema nativo.
   */
  @Bean
  @Qualifier( "validatorModel" )
  public ChatModel validatorModel() {
    var gemini = props.getGemini();

    log.info("Initializing validatorModel: model={}, provider=Google GenAI (temp=0, JSON schema)", gemini.getValidatorModel());

    var model = GoogleGenAiChatModel.builder()
        .genAiClient(geminiClient(gemini))
        .defaultOptions(GoogleGenAiChatOptions.builder()
            .model(gemini.getValidatorModel())
            .temperature(0.0)
            .maxOutputTokens(16384)
            .topP(1.0)
            .responseMimeType("application/json")
            .responseSchema(LlmResponseSchema.LORE_REVIEWER_SCHEMA)
            .build())
        .build();

    return new TokenAwareChatModelDecorator(model, "validatorModel", accountClient);
  }

  /**
   * RAG Query model — Gemini Flash per decomposizione query RAG. Temperature 0, JSON output con schema nativo.
   */
  @Bean
  @Qualifier( "ragQueryModel" )
  public ChatModel ragQueryModel() {
    var gemini = props.getGemini();

    log.info("Initializing ragQueryModel: model={}, provider=Google GenAI (temp=0, JSON schema)", gemini.getRagQueryModel());

    var model = GoogleGenAiChatModel.builder()
        .genAiClient(geminiClient(gemini))
        .defaultOptions(GoogleGenAiChatOptions.builder()
            .model(gemini.getRagQueryModel())
            .temperature(0.0)
            .maxOutputTokens(8192)
            .topP(1.0)
            .responseMimeType("application/json")
            .responseSchema(LlmResponseSchema.RAG_QUERY_SCHEMA)
            .build())
        .build();

    return new TokenAwareChatModelDecorator(model, "ragQueryModel", accountClient);
  }

  /**
   * Advisor model — Gemini per l'advisor (consigliere). Temperature bassa (0.2),
   * linguaggio naturale (no JSON enforced). Token tracking via decorator.
   */
  @Bean
  @Qualifier( "advisorModel" )
  public ChatModel advisorModel() {
    var gemini = props.getGemini();

    log.info("Initializing advisorModel: model={}, provider=Google GenAI (temp=0.2)", gemini.getAdvisorModel());

    var model = GoogleGenAiChatModel.builder()
        .genAiClient(geminiClient(gemini))
        .defaultOptions(GoogleGenAiChatOptions.builder()
            .model(gemini.getAdvisorModel())
            .temperature(0.2)
            .maxOutputTokens(16384)
            .topP(0.9).build())
        .build();

    return new TokenAwareChatModelDecorator(model, "advisorModel", accountClient);
  }

  /**
   * Setup Advisor model — Gemini per l'advisor di campaign setup. Temperature 0.35
   * (bassa per rispettare lore, non zero per fluency). Linguaggio naturale, no JSON.
   */
  @Bean
  @Qualifier( "setupAdvisorModel" )
  public ChatModel setupAdvisorModel() {
    var gemini = props.getGemini();

    log.info("Initializing setupAdvisorModel: model={}, provider=Google GenAI (temp=0.35)", gemini.getSetupAdvisorModel());

    var model = GoogleGenAiChatModel.builder()
        .genAiClient(geminiClient(gemini))
        .defaultOptions(GoogleGenAiChatOptions.builder()
            .model(gemini.getSetupAdvisorModel())
            .temperature(0.35)
            .maxOutputTokens(16384)
            .topP(0.9).build())
        .build();

    return new TokenAwareChatModelDecorator(model, "setupAdvisorModel", accountClient);
  }

  /**
   * Setup Summary model — Gemini per estrarre preferenze dalla chat advisor.
   * Temperature 0, linguaggio naturale (no JSON enforced). Output deterministico.
   */
  @Bean
  @Qualifier( "setupSummaryModel" )
  public ChatModel setupSummaryModel() {
    var gemini = props.getGemini();

    log.info("Initializing setupSummaryModel: model={}, provider=Google GenAI (temp=0)", gemini.getSetupSummaryModel());

    var model = GoogleGenAiChatModel.builder()
        .genAiClient(geminiClient(gemini))
        .defaultOptions(GoogleGenAiChatOptions.builder()
            .model(gemini.getSetupSummaryModel())
            .temperature(0.0)
            .maxOutputTokens(8192)
            .topP(1.0).build())
        .build();

    return new TokenAwareChatModelDecorator(model, "setupSummaryModel", accountClient);
  }

  /**
   * Name/Race Setup model — Gemini per scegliere razza e nome del personaggio.
   * Temperature 0.7 (creativo per nomi e varieta' razze), JSON output enforced con schema nativo.
   * Supporta tool calling (searchLore) per coerenza con la lore.
   */
  @Bean
  @Qualifier( "nameRaceSetupModel" )
  public ChatModel nameRaceSetupModel() {
    var gemini = props.getGemini();

    log.info("Initializing nameRaceSetupModel: model={}, provider=Google GenAI (temp=0.7, JSON schema + tools)",
        gemini.getNameRaceSetupModel());

    var model = GoogleGenAiChatModel.builder()
        .genAiClient(geminiClient(gemini))
        .defaultOptions(GoogleGenAiChatOptions.builder()
            .model(gemini.getNameRaceSetupModel())
            .temperature(0.7)
            .maxOutputTokens(4096)
            .topP(0.9)
            .responseMimeType("application/json")
            .responseSchema(LlmResponseSchema.NAME_RACE_SETUP_SCHEMA)
            .build())
        .build();

    return new TokenAwareChatModelDecorator(model, "nameRaceSetupModel", accountClient);
  }

  /**
   * Setup Generator model — Gemini per generazione character prompt e scena iniziale.
   * Temperature 0.7 (creativo, come gmModel). Linguaggio naturale, no JSON.
   */
  @Bean
  @Qualifier( "setupGeneratorModel" )
  public ChatModel setupGeneratorModel() {
    var gemini = props.getGemini();

    log.info("Initializing setupGeneratorModel: model={}, provider=Google GenAI (temp=0.7)",
        gemini.getSetupGeneratorModel());

    var model = GoogleGenAiChatModel.builder()
        .genAiClient(geminiClient(gemini))
        .defaultOptions(GoogleGenAiChatOptions.builder()
            .model(gemini.getSetupGeneratorModel())
            .temperature(0.7)
            .maxOutputTokens(16384)
            .topP(0.9).build())
        .build();

    return new TokenAwareChatModelDecorator(model, "setupGeneratorModel", accountClient);
  }

  /**
   * Embedding model (Gemini cloud) — gemini-embedding-001 via Google GenAI.
   * Dual-task wrapper: RETRIEVAL_QUERY per similarity search, RETRIEVAL_DOCUMENT per ETL.
   */
  @Primary
  @Bean
  public EmbeddingModel embeddingModel() {
    OndgardAiProperties.GeminiProperties gemini = props.getGemini();

    log.info("Initializing embeddingModel (Gemini): model={}, dimensions={}", gemini.getEmbeddingModel(), gemini.getEmbeddingDimensions());

    var connectionDetails = GoogleGenAiEmbeddingConnectionDetails.builder()
        .genAiClient(geminiClient(gemini))
        .build();

    var queryOptions = GoogleGenAiTextEmbeddingOptions.builder()
        .model(gemini.getEmbeddingModel())
        .dimensions(gemini.getEmbeddingDimensions())
        .taskType(TaskType.RETRIEVAL_QUERY)
        .build();

    var indexingOptions = GoogleGenAiTextEmbeddingOptions.builder()
        .model(gemini.getEmbeddingModel())
        .dimensions(gemini.getEmbeddingDimensions())
        .taskType(TaskType.RETRIEVAL_DOCUMENT)
        .build();

    var queryModel = new GoogleGenAiTextEmbeddingModel(connectionDetails, queryOptions);
    var indexingModel = new GoogleGenAiTextEmbeddingModel(connectionDetails, indexingOptions);

    return new GeminiDualTaskEmbeddingModel(queryModel, indexingModel, gemini.getEmbeddingTpmLimit(), gemini.getEmbeddingDimensions());
  }

  /**
   * Local Gpu model — deterministico, gira su ollama-gpu.
   */
  @Bean
  @Qualifier( "localGpuModel" )
  public ChatModel lightModel() {
    var gpu = props.getOllamaGpu();
    var opts = gpu.getOptions();

    log.info("Initializing localGpuModel: model={}, url={}", gpu.getModel(), gpu.getBaseUrl());

    var optionsBuilder = OllamaChatOptions.builder().model(gpu.getModel()).temperature(opts.getTemperature()).numPredict(opts.getNumPredict()).topP(opts.getTopP()).format("json");

    if( opts.getNumCtx() != null ){
      optionsBuilder.numCtx(opts.getNumCtx());
    }
    if( opts.getTopK() != null ){
      optionsBuilder.topK(opts.getTopK());
    }

    var api = OllamaApi.builder().baseUrl(gpu.getBaseUrl()).restClientBuilder(longTimeoutRestClientBuilder()).build();

    return OllamaChatModel.builder().ollamaApi(api).defaultOptions(optionsBuilder.build()).build();
  }

  /**
   * Embedding model locale (Ollama CPU) — qwen3-embedding.
   * Mantenuto come opzione locale, non @Primary.
   */
  @Bean
  @Qualifier( "localEmbeddingModel" )
  public EmbeddingModel localEmbeddingModel() {
    var cpu = props.getOllamaCpu();

    log.info("Initializing localEmbeddingModel: model={}, url={}", cpu.getEmbeddingModel(), cpu.getBaseUrl());

    var api = OllamaApi.builder().baseUrl(cpu.getBaseUrl()).restClientBuilder(longTimeoutRestClientBuilder()).build();

    return OllamaEmbeddingModel.builder().ollamaApi(api).defaultOptions(OllamaEmbeddingOptions.builder().model(cpu.getEmbeddingModel()).build()).build();
  }

  // ************************************ private ************************************

  private Client geminiClient(OndgardAiProperties.GeminiProperties gemini) {
    var httpOpts = com.google.genai.types.HttpOptions.builder().timeout((int) LLM_READ_TIMEOUT.toMillis());
    return Client.builder().apiKey(gemini.getApiKey()).httpOptions(httpOpts.build()).build();
  }

  private static RestClient.Builder longTimeoutRestClientBuilder() {
    var requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(LLM_READ_TIMEOUT);
    return RestClient.builder().requestFactory(requestFactory);
  }
}
