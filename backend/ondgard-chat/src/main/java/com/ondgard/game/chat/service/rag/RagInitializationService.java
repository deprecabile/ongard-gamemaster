package com.ondgard.game.chat.service.rag;

import com.ondgard.game.chat.config.ai.LoreMetadataTag;
import com.ondgard.game.chat.config.ai.RagProperties;
import com.ondgard.game.chat.service.LoreProvider;
import com.ondgard.game.chat.service.LoreRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagInitializationService {

  private final LoreProvider loreProvider;
  private final VectorStore vectorStore;
  private final TokenTextSplitter tokenTextSplitter;
  private final RagReadinessGate ragReadinessGate;
  private final RagProperties ragProperties;
  private final StringRedisTemplate stringRedisTemplate;
  private final RedisMessageListenerContainer redisMessageListenerContainer;
  private final JedisPooled jedisPooled;

  @EventListener( ApplicationReadyEvent.class )
  void onApplicationReady() {
    String currentHash = loreProvider.getContentHash();
    String storedHash = stringRedisTemplate.opsForValue().get(ragProperties.getContentHashKey());

    if( currentHash.equals(storedHash) ){
      log.info("RAG — Content hash matches stored hash, skipping re-indexing");
      markReady();
      return;
    }

    log.info("RAG — Content hash mismatch (stored={}, current={}), waiting for init trigger...",
        storedHash, currentHash);
    subscribeToInitTopic();
  }

  private void subscribeToInitTopic() {
    String topic = ragProperties.getInitTopic();
    log.debug("RAG — Subscribing to Redis topic '{}', waiting for init signal...", topic);

    MessageListener listener = (message, pattern) -> {
      log.info("RAG — Received init signal on topic '{}'", topic);
      reindex();
    };

    redisMessageListenerContainer.addMessageListener(listener, new ChannelTopic(topic));
  }

  void reindex() {
    clearExistingDocuments();
    initializeVectorStore();
    stringRedisTemplate.opsForValue().set(
        ragProperties.getContentHashKey(), loreProvider.getContentHash());
    markReady();
  }

  private void clearExistingDocuments() {
    Set<String> keys = jedisPooled.keys("embedding:*");
    if( keys != null && !keys.isEmpty() ){
      jedisPooled.del(keys.toArray(String[]::new));
      log.info("RAG — Cleared {} existing documents from Redis", keys.size());
    }
  }

  void initializeVectorStore() {
    log.info("RAG ETL — Starting vector store initialization...");

    List<Document> documents = new ArrayList<>();
    for( String lang : loreProvider.getAvailableLanguages() ){
      LoreRegistry view = loreProvider.get(lang);
      for( String category : view.getCategoryKeys() ){
        String content = view.getLore(category);
        if( content.isEmpty() ) continue;

        documents.add(new Document(content, Map.of(
            LoreMetadataTag.CATEGORY.getKey(), category,
            LoreMetadataTag.SCENARIO.getKey(), "ondgard",
            LoreMetadataTag.LANGUAGE.getKey(), lang
        )));
        log.debug("RAG ETL — Prepared document for [{}/{}] ({} chars)", lang, category, content.length());
      }
    }

    List<Document> chunks = tokenTextSplitter.apply(documents);
    log.info("RAG ETL — Chunked {} documents into {} chunks", documents.size(), chunks.size());

    vectorStore.add(chunks);
    log.info("RAG ETL — Vector store initialization complete. {} chunks indexed.", chunks.size());
  }

  private void markReady() {
    ragReadinessGate.markReady();
    log.info("RAG readiness gate opened");
    stringRedisTemplate.convertAndSend(ragProperties.getReadyTopic(), "ok");
  }
}
