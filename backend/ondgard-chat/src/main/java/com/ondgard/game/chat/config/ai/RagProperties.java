package com.ondgard.game.chat.config.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties( prefix = "ondgard.rag" )
public class RagProperties {

  private int chunkSize = 1000;
  private int minChunkSizeChars = 350;
  // GM retrieval
  private int retrievalTopK = 10;
  private double retrievalSimilarityThreshold = 0.5;
  // Router category detection (per-sentence RAG)
  private int routerTopK = 5;
  private double routerSimilarityThreshold = 0.45;
  private int routerChunkSize = 300;
  // Scene lore retrieval
  private int sceneTopK = 30;
  private double sceneSimilarityThreshold = 0.3;
  private String initTopic = "ondgard:rag:init";
  private String readyTopic = "ondgard:rag:ready";
  private String contentHashKey = "rag:ondgard:content-hash";
}
