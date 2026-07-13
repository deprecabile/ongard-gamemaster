package com.ondgard.game.chat.config.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties( RagProperties.class )
public class RagConfig {

  private final RagProperties ragProperties;

  @Bean
  public JedisPooled jedisPooled(
      @Value( "${spring.data.redis.host:localhost}" ) String host,
      @Value( "${spring.data.redis.port:6379}" ) int port) {
    return new JedisPooled(host, port);
  }

  @Bean
  public VectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
    RedisVectorStore redisStore = RedisVectorStore.builder(jedisPooled, embeddingModel)
        .metadataFields(
            MetadataField.tag(LoreMetadataTag.SCENARIO.getKey()),
            MetadataField.tag(LoreMetadataTag.LANGUAGE.getKey()),
            MetadataField.tag(LoreMetadataTag.CATEGORY.getKey()))
        .initializeSchema(true)
        .build();
    redisStore.afterPropertiesSet();

    if( embeddingModel instanceof EmbeddingModelPreloader preloader ){
      return new BatchingVectorStore(redisStore, preloader);
    }
    return redisStore;
  }

  @Bean
  public TokenTextSplitter tokenTextSplitter() {
    return TokenTextSplitter.builder()
        .withChunkSize(ragProperties.getChunkSize())
        .withMinChunkSizeChars(ragProperties.getMinChunkSizeChars())
        .withMinChunkLengthToEmbed(ragProperties.getMinChunkSizeChars())
        .withMaxNumChunks(Integer.MAX_VALUE)
        .withKeepSeparator(true)
        .build();
  }
}
