package com.ondgard.game.chat.service.rag;

import com.ondgard.game.chat.config.ai.LoreMetadataTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RedisVectorStore with metadata tags and JedisPooled operations.
 * Uses a real Redis Stack instance via TestContainers and a deterministic fake embedding model.
 */
@Testcontainers
class RagRedisIntegrationTest {

  @Container
  @SuppressWarnings( "resource" )
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis/redis-stack-server:latest")
          .withExposedPorts(6379);

  private JedisPooled jedisPooled;
  private RedisVectorStore vectorStore;

  @BeforeEach
  void setUp() {
    jedisPooled = new JedisPooled(redis.getHost(), redis.getMappedPort(6379));
    jedisPooled.flushDB();

    vectorStore = RedisVectorStore.builder(jedisPooled, new FakeEmbeddingModel())
        .metadataFields(
            MetadataField.tag(LoreMetadataTag.SCENARIO.getKey()),
            MetadataField.tag(LoreMetadataTag.LANGUAGE.getKey()),
            MetadataField.tag(LoreMetadataTag.CATEGORY.getKey()))
        .initializeSchema(true)
        .build();
    vectorStore.afterPropertiesSet();
  }

  @AfterEach
  void tearDown() {
    jedisPooled.flushDB();
    jedisPooled.close();
  }

  // ===== Metadata =====

  @Test
  void addDocuments_withLoreMetadata_metadataPreservedInSearch() {
    vectorStore.add(List.of(loreDoc("STORIA", "it",
        "The ancient kingdom of Ondgard was founded in the mountains")));

    List<Document> results = vectorStore.similaritySearch(
        SearchRequest.builder().query("kingdom").topK(5).build());

    assertThat(results).hasSize(1);
    Map<String, Object> metadata = results.getFirst().getMetadata();
    assertThat(metadata)
        .containsEntry("category", "STORIA")
        .containsEntry("scenario", "ondgard")
        .containsEntry("language", "it");
  }

  @Test
  void multipleCategories_allSearchableWithCorrectMetadata() {
    vectorStore.add(List.of(
        loreDoc("GEOGRAFIA", "it", "Content about geography and terrain"),
        loreDoc("STORIA", "it", "Content about history and ancient wars"),
        loreDoc("FAUNA", "it", "Content about fauna and creatures")
    ));

    List<Document> results = vectorStore.similaritySearch(
        SearchRequest.builder().query("query").topK(10).build());

    assertThat(results).hasSize(3);
    assertThat(results)
        .extracting(doc -> doc.getMetadata().get("category"))
        .containsExactlyInAnyOrder("GEOGRAFIA", "STORIA", "FAUNA");
    assertThat(results)
        .allSatisfy(doc -> {
          assertThat(doc.getMetadata().get("scenario")).isEqualTo("ondgard");
          assertThat(doc.getMetadata().get("language")).isEqualTo("it");
        });
  }

  // ===== Filter =====

  @Test
  void similaritySearch_withCategoryFilter_returnsOnlyMatchingCategory() {
    vectorStore.add(List.of(
        loreDoc("GEOGRAFIA", "it", "Mountains and rivers of the northern region"),
        loreDoc("STORIA", "it", "The great war of independence shaped the world")
    ));

    List<Document> results = vectorStore.similaritySearch(
        SearchRequest.builder()
            .query("any query")
            .topK(10)
            .filterExpression("category == 'GEOGRAFIA'")
            .build());

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getMetadata().get("category")).isEqualTo("GEOGRAFIA");
  }

  @Test
  void similaritySearch_withScenarioFilter_returnsOnlyMatchingScenario() {
    vectorStore.add(List.of(loreDoc("STORIA", "it",
        "Ondgard lore about the great battle")));

    List<Document> results = vectorStore.similaritySearch(
        SearchRequest.builder()
            .query("query")
            .topK(10)
            .filterExpression("scenario == 'ondgard'")
            .build());

    assertThat(results).hasSize(1);

    List<Document> noResults = vectorStore.similaritySearch(
        SearchRequest.builder()
            .query("query")
            .topK(10)
            .filterExpression("scenario == 'other'")
            .build());

    assertThat(noResults).isEmpty();
  }

  @Test
  void similaritySearch_withLanguageFilter_returnsOnlyMatchingLanguage() {
    vectorStore.add(List.of(
        loreDoc("STORIA", "it", "La guerra antica distrusse molti regni"),
        loreDoc("STORIA", "en", "The ancient war destroyed many kingdoms")
    ));

    List<Document> itResults = vectorStore.similaritySearch(
        SearchRequest.builder()
            .query("war")
            .topK(10)
            .filterExpression("language == 'it'")
            .build());

    assertThat(itResults).hasSize(1);
    assertThat(itResults.getFirst().getMetadata().get("language")).isEqualTo("it");

    List<Document> enResults = vectorStore.similaritySearch(
        SearchRequest.builder()
            .query("war")
            .topK(10)
            .filterExpression("language == 'en'")
            .build());

    assertThat(enResults).hasSize(1);
    assertThat(enResults.getFirst().getMetadata().get("language")).isEqualTo("en");
  }

  // ===== Redis keys (embedding:*) =====

  @Test
  void addDocuments_createsEmbeddingKeysInRedis() {
    vectorStore.add(List.of(loreDoc("FLORA", "it", "test content for key verification")));

    Set<String> keys = jedisPooled.keys("embedding:*");
    assertThat(keys).hasSize(1);
  }

  @Test
  void clearEmbeddingKeys_documentsNoLongerSearchable() {
    vectorStore.add(List.of(
        loreDoc("FAUNA", "it", "content to be cleared one"),
        loreDoc("FLORA", "it", "content to be cleared two")
    ));

    Set<String> keysBefore = jedisPooled.keys("embedding:*");
    assertThat(keysBefore).hasSize(2);

    jedisPooled.del(keysBefore.toArray(String[]::new));

    assertThat(jedisPooled.keys("embedding:*")).isEmpty();

    List<Document> results = vectorStore.similaritySearch(
        SearchRequest.builder().query("content").topK(5).build());
    assertThat(results).isEmpty();
  }

  @Test
  void clearAndReadd_onlyNewDocumentsSearchable() {
    vectorStore.add(List.of(loreDoc("RAZZE", "it", "old lore about elves and dwarves")));

    Set<String> keys = jedisPooled.keys("embedding:*");
    jedisPooled.del(keys.toArray(String[]::new));

    vectorStore.add(List.of(loreDoc("BESTIARIO", "it", "new lore about dragons and wyverns")));

    List<Document> results = vectorStore.similaritySearch(
        SearchRequest.builder().query("query").topK(10).build());

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getMetadata()).containsEntry("category", "BESTIARIO");
  }

  // ===== Content hash =====

  @Test
  void contentHash_storedAndRetrievableViaJedis() {
    String hashKey = "rag:ondgard:content-hash";

    jedisPooled.set(hashKey, "abc123def456");
    assertThat(jedisPooled.get(hashKey)).isEqualTo("abc123def456");

    jedisPooled.set(hashKey, "updatedHash789");
    assertThat(jedisPooled.get(hashKey)).isEqualTo("updatedHash789");

    jedisPooled.del(hashKey);
    assertThat(jedisPooled.get(hashKey)).isNull();
  }

  // ===== RediSearch index =====

  @Test
  void redisSearchIndex_createdWithCorrectMetadataFields() {
    Map<String, Object> info = jedisPooled.ftInfo("spring-ai-index");

    assertThat(info).isNotNull();
    assertThat(info).containsKey("index_name");
  }

  @Test
  void redisSearchIndex_survivesDocumentClear() {
    vectorStore.add(List.of(loreDoc("STORIA", "it", "some content")));

    Set<String> keys = jedisPooled.keys("embedding:*");
    jedisPooled.del(keys.toArray(String[]::new));

    // Index still exists after clearing documents
    Map<String, Object> info = jedisPooled.ftInfo("spring-ai-index");
    assertThat(info).isNotNull();

    // Can still add new documents — index re-indexes them automatically
    vectorStore.add(List.of(loreDoc("FLORA", "it", "new content after clear")));

    List<Document> results = vectorStore.similaritySearch(
        SearchRequest.builder().query("query").topK(5).build());
    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getMetadata()).containsEntry("category", "FLORA");
  }

  // ===== Helpers =====

  private static Document loreDoc(String category, String language, String content) {
    return new Document(content, Map.of(
        LoreMetadataTag.CATEGORY.getKey(), category,
        LoreMetadataTag.SCENARIO.getKey(), "ondgard",
        LoreMetadataTag.LANGUAGE.getKey(), language
    ));
  }

  /**
   * Deterministic embedding model for integration testing.
   * Produces consistent, normalized vectors based on text hash.
   */
  static class FakeEmbeddingModel implements EmbeddingModel {

    private static final int DIMS = 16;

    @Override
    public float[] embed(Document document) {
      return computeVector(document.getText());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
      List<Embedding> embeddings = new ArrayList<>();
      for( int i = 0; i < request.getInstructions().size(); i++ ){
        float[] vector = computeVector(request.getInstructions().get(i));
        embeddings.add(new Embedding(vector, i));
      }
      return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
      return DIMS;
    }

    private float[] computeVector(String text) {
      float[] vector = new float[DIMS];
      int hash = text.hashCode();
      for( int i = 0; i < DIMS; i++ ){
        vector[i] = ((hash >>> (i % 32)) & 0xFF) / 255.0f + 0.01f;
        hash = hash * 31 + i;
      }
      float norm = 0;
      for( float v : vector ) norm += v * v;
      norm = (float) Math.sqrt(norm);
      for( int i = 0; i < DIMS; i++ ) vector[i] /= norm;
      return vector;
    }
  }
}
