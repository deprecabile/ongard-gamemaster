package com.ondgard.game.chat.config.ai;

import org.springframework.ai.document.Document;

import java.util.Collection;

/**
 * Capability interface: an EmbeddingModel that can batch-preload
 * embeddings to avoid per-document API calls.
 */
public interface EmbeddingModelPreloader {

  void preloadForIndexing(Collection<Document> documents);

}
