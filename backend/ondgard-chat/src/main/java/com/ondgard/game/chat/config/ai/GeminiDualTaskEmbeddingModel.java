package com.ondgard.game.chat.config.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin wrapper that routes embedding calls to two {@link GoogleGenAiTextEmbeddingModel}
 * instances configured with different task types.
 * <p>
 * SimpleVectorStore routing:
 * <ul>
 *   <li>{@code doAdd(docs)} → {@link #embed(Document)} → {@code indexingModel} (RETRIEVAL_DOCUMENT)</li>
 *   <li>{@code doSimilaritySearch(q)} → {@link #embed(String)} → {@link #call} → {@code queryModel} (RETRIEVAL_QUERY)</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class GeminiDualTaskEmbeddingModel implements EmbeddingModel, EmbeddingModelPreloader {

  private final GoogleGenAiTextEmbeddingModel queryModel;    // RETRIEVAL_QUERY
  private final GoogleGenAiTextEmbeddingModel indexingModel;  // RETRIEVAL_DOCUMENT
  private final int tpmSafetyLimit;
  private final int embeddingDimensions;

  private final ConcurrentHashMap<String, float[]> indexingCache = new ConcurrentHashMap<>();

  private static final int BATCH_SIZE = 10;
  private static final int MAX_RETRIES = 3;
  private static final long RETRY_WAIT_MS = 61_000;

  @Override
  public void preloadForIndexing(Collection<Document> documents) {
    List<String> texts = documents.stream()
        .map(Document::getText)
        .toList();

    log.info("Preloading {} embeddings in sub-batches of {}...", texts.size(), BATCH_SIZE);

    int tokensSentInWindow = 0;

    for( int from = 0; from < texts.size(); from += BATCH_SIZE ){
      int to = Math.min(from + BATCH_SIZE, texts.size());
      List<String> batch = texts.subList(from, to);
      int batchTokens = estimateTokens(batch);

      if( tokensSentInWindow > 0 && tokensSentInWindow + batchTokens > tpmSafetyLimit ){
        log.info("TPM budget reached (~{} tokens), waiting 61s for window reset...", tokensSentInWindow);
        sleep(RETRY_WAIT_MS);
        tokensSentInWindow = 0;
      }

      EmbeddingResponse response = callWithRetry(batch, from, to, texts.size(), batchTokens);

      for( int i = 0; i < batch.size(); i++ ){
        indexingCache.put(batch.get(i), response.getResults().get(i).getOutput());
      }

      tokensSentInWindow += batchTokens;
    }
  }

  private EmbeddingResponse callWithRetry(List<String> batch, int from, int to, int total, int batchTokens) {
    for( int attempt = 1; attempt <= MAX_RETRIES; attempt++ ){
      try{
        log.info("Embedding sub-batch [{}-{}) of {} (~{} tokens), attempt {}/{}",
            from, to, total, batchTokens, attempt, MAX_RETRIES);
        return indexingModel.call(new EmbeddingRequest(batch, null));
      }catch(Exception e){
        log.warn("Sub-batch [{}-{}) failed (attempt {}/{}): {}", from, to, attempt, MAX_RETRIES, e.getMessage());
        if( attempt == MAX_RETRIES ){
          throw e;
        }
        log.info("Waiting 61s before retry...");
        sleep(RETRY_WAIT_MS);
      }
    }
    throw new IllegalStateException("Unreachable");
  }

  private int estimateTokens(List<String> texts) {
    return texts.stream().mapToInt(t -> t.length() / 4).sum();
  }

  private void sleep(long ms) {
    try{
      Thread.sleep(ms);
    }catch(InterruptedException e){
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public List<float[]> embed(List<Document> documents, EmbeddingOptions options,
                             BatchingStrategy batchingStrategy) {
    List<float[]> embeddings = new ArrayList<>(documents.size());
    List<Integer> uncachedIndices = new ArrayList<>();

    for( int i = 0; i < documents.size(); i++ ){
      float[] cached = indexingCache.remove(documents.get(i).getText());
      if( cached != null ){
        embeddings.add(cached);
      } else{
        embeddings.add(null);
        uncachedIndices.add(i);
      }
    }

    if( !uncachedIndices.isEmpty() ){
      List<String> texts = uncachedIndices.stream()
          .map(i -> documents.get(i).getText())
          .toList();
      EmbeddingResponse response = indexingModel.call(new EmbeddingRequest(texts, options));
      for( int j = 0; j < uncachedIndices.size(); j++ ){
        embeddings.set(uncachedIndices.get(j), response.getResults().get(j).getOutput());
      }
    }

    return embeddings;
  }

  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    return queryModel.call(request);
  }

  @Override
  public float[] embed(Document document) {
    float[] cached = indexingCache.remove(document.getText());
    if( cached != null ){
      return cached;
    }
    return indexingModel.embed(document);
  }

  @Override
  public int dimensions() {
    return embeddingDimensions;
  }
}
