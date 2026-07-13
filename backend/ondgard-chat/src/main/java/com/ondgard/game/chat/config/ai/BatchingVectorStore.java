package com.ondgard.game.chat.config.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;

/**
 * Decorator that intercepts {@link #add(List)} to batch-preload embeddings
 * via an {@link EmbeddingModelPreloader} before delegating to the wrapped store.
 * All other operations are delegated directly.
 */
@RequiredArgsConstructor
public class BatchingVectorStore implements VectorStore {

  private final VectorStore delegate;
  private final EmbeddingModelPreloader preloader;

  @Override
  public void add(List<Document> documents) {
    preloader.preloadForIndexing(documents);
    delegate.add(documents);
  }

  @Override
  public void delete(List<String> idList) {
    delegate.delete(idList);
  }

  @Override
  public void delete(Filter.Expression filterExpression) {
    delegate.delete(filterExpression);
  }

  @Override
  public List<Document> similaritySearch(SearchRequest request) {
    return delegate.similaritySearch(request);
  }
}
