package com.ondgard.game.chat.service.rag;

import com.ondgard.game.chat.config.ai.RagProperties;
import com.ondgard.game.chat.service.LoreProvider;
import com.ondgard.game.chat.service.LoreRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import redis.clients.jedis.JedisPooled;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith( MockitoExtension.class )
class RagInitializationServiceTest {

  @Mock
  private LoreProvider loreProvider;
  @Mock
  private LoreRegistry loreRegistry;
  @Mock
  private VectorStore vectorStore;
  @Mock
  private TokenTextSplitter tokenTextSplitter;
  @Mock
  private StringRedisTemplate stringRedisTemplate;
  @Mock
  private ValueOperations<String, String> valueOperations;
  @Mock
  private RedisMessageListenerContainer redisMessageListenerContainer;
  @Mock
  private JedisPooled jedisPooled;

  @Captor
  private ArgumentCaptor<List<Document>> docsCaptor;

  private RagReadinessGate ragReadinessGate;
  private RagProperties ragProperties;
  private RagInitializationService service;

  @BeforeEach
  void setUp() {
    ragReadinessGate = new RagReadinessGate();
    ragProperties = new RagProperties();
    service = new RagInitializationService(
        loreProvider, vectorStore, tokenTextSplitter,
        ragReadinessGate, ragProperties, stringRedisTemplate,
        redisMessageListenerContainer, jedisPooled
    );
  }

  @Test
  void initializeVectorStore_createsDocumentsPerCategory() {
    when(loreProvider.getAvailableLanguages()).thenReturn(Set.of("it"));
    when(loreProvider.get("it")).thenReturn(loreRegistry);
    when(loreRegistry.getCategoryKeys()).thenReturn(Set.of("GEOGRAFIA", "STORIA"));
    when(loreRegistry.getLore("GEOGRAFIA")).thenReturn("Mountains and rivers...");
    when(loreRegistry.getLore("STORIA")).thenReturn("Ancient kingdoms...");

    List<Document> fakeChunks = List.of(
        new Document("chunk1", Map.of("category", "GEOGRAFIA")),
        new Document("chunk2", Map.of("category", "STORIA"))
    );
    when(tokenTextSplitter.apply(anyList())).thenReturn(fakeChunks);

    service.initializeVectorStore();

    verify(vectorStore).add(fakeChunks);
  }

  @Test
  void initializeVectorStore_setsMetadataWithScenarioAndLanguage() {
    when(loreProvider.getAvailableLanguages()).thenReturn(Set.of("it"));
    when(loreProvider.get("it")).thenReturn(loreRegistry);
    when(loreRegistry.getCategoryKeys()).thenReturn(Set.of("GEOGRAFIA", "STORIA"));
    when(loreRegistry.getLore("GEOGRAFIA")).thenReturn("Mountains and rivers...");
    when(loreRegistry.getLore("STORIA")).thenReturn("Ancient kingdoms...");
    when(tokenTextSplitter.apply(anyList())).thenReturn(List.of());

    service.initializeVectorStore();

    verify(tokenTextSplitter).apply(docsCaptor.capture());
    List<Document> documents = docsCaptor.getValue();

    assertThat(documents).hasSize(2);
    assertThat(documents)
        .extracting(doc -> doc.getMetadata().get("category"))
        .containsExactlyInAnyOrder("GEOGRAFIA", "STORIA");
    assertThat(documents)
        .allSatisfy(doc -> {
          assertThat(doc.getMetadata().get("scenario")).isEqualTo("ondgard");
          assertThat(doc.getMetadata().get("language")).isEqualTo("it");
        });
  }

  @Test
  void initializeVectorStore_skipsEmptyCategories() {
    when(loreProvider.getAvailableLanguages()).thenReturn(Set.of("it"));
    when(loreProvider.get("it")).thenReturn(loreRegistry);
    when(loreRegistry.getCategoryKeys()).thenReturn(Set.of("GEOGRAFIA", "VUOTA"));
    when(loreRegistry.getLore("GEOGRAFIA")).thenReturn("Mountains...");
    when(loreRegistry.getLore("VUOTA")).thenReturn("");
    when(tokenTextSplitter.apply(anyList())).thenReturn(List.of());

    service.initializeVectorStore();

    verify(tokenTextSplitter).apply(docsCaptor.capture());
    List<Document> documents = docsCaptor.getValue();

    assertThat(documents).hasSize(1);
    assertThat(documents.getFirst().getMetadata().get("category")).isEqualTo("GEOGRAFIA");
  }

  @Test
  void initializeVectorStore_handlesMultipleLanguages() {
    LoreRegistry enRegistry = mock(LoreRegistry.class);
    when(loreProvider.getAvailableLanguages()).thenReturn(Set.of("en", "it"));
    when(loreProvider.get("it")).thenReturn(loreRegistry);
    when(loreProvider.get("en")).thenReturn(enRegistry);
    when(loreRegistry.getCategoryKeys()).thenReturn(Set.of("GEOGRAFIA"));
    when(loreRegistry.getLore("GEOGRAFIA")).thenReturn("Montagne...");
    when(enRegistry.getCategoryKeys()).thenReturn(Set.of("GEOGRAPHY"));
    when(enRegistry.getLore("GEOGRAPHY")).thenReturn("Mountains...");
    when(tokenTextSplitter.apply(anyList())).thenReturn(List.of());

    service.initializeVectorStore();

    verify(tokenTextSplitter).apply(docsCaptor.capture());
    List<Document> documents = docsCaptor.getValue();

    assertThat(documents).hasSize(2);
    assertThat(documents)
        .anySatisfy(doc -> {
          assertThat(doc.getMetadata().get("category")).isEqualTo("GEOGRAFIA");
          assertThat(doc.getMetadata().get("language")).isEqualTo("it");
        })
        .anySatisfy(doc -> {
          assertThat(doc.getMetadata().get("category")).isEqualTo("GEOGRAPHY");
          assertThat(doc.getMetadata().get("language")).isEqualTo("en");
        });
  }

  @Test
  void onApplicationReady_hashMatches_marksReadyImmediately() {
    when(loreProvider.getContentHash()).thenReturn("abc123");
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("rag:ondgard:content-hash")).thenReturn("abc123");

    service.onApplicationReady();

    assertThat(ragReadinessGate.isReady()).isTrue();
    verify(vectorStore, never()).add(anyList());
    verify(redisMessageListenerContainer, never()).addMessageListener(any(MessageListener.class), any(ChannelTopic.class));
  }

  @Test
  void onApplicationReady_hashMismatch_subscribesToInitTopic() {
    when(loreProvider.getContentHash()).thenReturn("newHash");
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("rag:ondgard:content-hash")).thenReturn("oldHash");

    service.onApplicationReady();

    assertThat(ragReadinessGate.isReady()).isFalse();
    verify(redisMessageListenerContainer).addMessageListener(any(MessageListener.class), any(ChannelTopic.class));
  }

  @Test
  void onApplicationReady_noStoredHash_subscribesToInitTopic() {
    when(loreProvider.getContentHash()).thenReturn("someHash");
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("rag:ondgard:content-hash")).thenReturn(null);

    service.onApplicationReady();

    assertThat(ragReadinessGate.isReady()).isFalse();
    verify(redisMessageListenerContainer).addMessageListener(any(MessageListener.class), any(ChannelTopic.class));
  }

  @Test
  void reindex_clearsDocuments_indexes_storesHash_opensGate() {
    when(jedisPooled.keys("embedding:*")).thenReturn(Set.of("embedding:1", "embedding:2"));
    when(loreProvider.getAvailableLanguages()).thenReturn(Set.of("it"));
    when(loreProvider.get("it")).thenReturn(loreRegistry);
    when(loreRegistry.getCategoryKeys()).thenReturn(Set.of("GEOGRAFIA"));
    when(loreRegistry.getLore("GEOGRAFIA")).thenReturn("Mountains...");
    when(loreProvider.getContentHash()).thenReturn("newHash123");
    when(tokenTextSplitter.apply(anyList())).thenReturn(List.of(new Document("chunk1")));
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

    service.reindex();

    // Cleared old documents
    ArgumentCaptor<String[]> keysCaptor = ArgumentCaptor.forClass(String[].class);
    verify(jedisPooled).del(keysCaptor.capture());
    assertThat(keysCaptor.getValue()).containsExactlyInAnyOrder("embedding:1", "embedding:2");
    // Indexed new documents
    verify(vectorStore).add(anyList());
    // Stored new hash
    verify(valueOperations).set("rag:ondgard:content-hash", "newHash123");
    // Gate is open
    assertThat(ragReadinessGate.isReady()).isTrue();
    // Published ready event
    verify(stringRedisTemplate).convertAndSend(eq("ondgard:rag:ready"), eq("ok"));
  }
}
