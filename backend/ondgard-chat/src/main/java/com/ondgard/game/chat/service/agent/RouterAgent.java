package com.ondgard.game.chat.service.agent;

import com.ondgard.game.chat.config.ai.LoreMetadataTag;
import com.ondgard.game.chat.config.ai.RagProperties;
import com.ondgard.game.chat.service.LoreProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouterAgent {

  private final LoreProvider loreProvider;
  private final VectorStore vectorStore;
  private final RagProperties ragProperties;
  @Qualifier( "parallelExecutor" ) private final ExecutorService parallelExecutor;

  private static final Pattern MARKDOWN_PATTERN = Pattern.compile(
      "\\*{1,3}|_{1,3}|`{1,3}|#{1,6}\\s|\\[([^]]*)]\\([^)]*\\)|!\\[([^]]*)]\\([^)]*\\)|^\\s*[-*+]\\s|^\\s*\\d+\\.\\s|^>\\s?",
      Pattern.MULTILINE);

  public Collection<String> route(String loreLangCode, String draft) {
    Set<String> validCategories = loreProvider.get(loreLangCode).getCategoryKeys();
    Collection<String> chunks = splitIntoChunks(draft);

    if( chunks.isEmpty() ){
      log.info("Router: no chunks extracted from draft — returning empty themes");
      return List.of();
    }

    log.debug("Router: split draft into {} chunks (chunk size {})", chunks.size(), ragProperties.getRouterChunkSize());

    Collection<CompletableFuture<Set<String>>> futures = chunks.stream()
        .map(chunk -> CompletableFuture.supplyAsync(() -> searchCategories(chunk, loreLangCode), parallelExecutor))
        .toList();

    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

    Set<String> categories = futures.stream()
        .map(CompletableFuture::join)
        .flatMap(Set::stream)
        .filter(validCategories::contains)
        .collect(Collectors.toUnmodifiableSet());

    log.info("Router themes (per-chunk RAG, {} chunks): {}", chunks.size(), categories);
    return categories;
  }

  // ************************************ private ************************************

  private Collection<String> splitIntoChunks(String draft) {
    String cleanText = MARKDOWN_PATTERN.matcher(draft).replaceAll("$1$2").trim();
    if( cleanText.isEmpty() ){
      return List.of();
    }

    final int chunkSize = ragProperties.getRouterChunkSize();
    final Collection<String> chunks = new ArrayList<>((cleanText.length() / chunkSize) + 1);

    for( int i = 0; i < cleanText.length(); i += chunkSize ){
      String chunk = cleanText.substring(i, Math.min(i + chunkSize, cleanText.length())).trim();
      if( !chunk.isEmpty() ){
        chunks.add(chunk);
      }
    }

    return chunks;
  }

  private Set<String> searchCategories(String sentence, String loreLangCode) {
    List<Document> results = vectorStore.similaritySearch(
        SearchRequest.builder()
            .query(sentence)
            .topK(ragProperties.getRouterTopK())
            .similarityThreshold(ragProperties.getRouterSimilarityThreshold())
            .filterExpression(LoreMetadataTag.LANGUAGE.getKey() + " == '" + loreLangCode + "'")
            .build());

    return results.stream()
        .map(doc -> (String) doc.getMetadata().getOrDefault(LoreMetadataTag.CATEGORY.getKey(), ""))
        .filter(cat -> !cat.isEmpty())
        .map(String::toUpperCase)
        .collect(Collectors.toSet());
  }
}
