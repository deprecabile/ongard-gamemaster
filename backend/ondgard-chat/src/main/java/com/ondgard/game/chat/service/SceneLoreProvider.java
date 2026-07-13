package com.ondgard.game.chat.service;

import com.ondgard.game.chat.config.ai.RagProperties;
import com.ondgard.game.chat.service.rag.RagReadinessGate;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SceneLoreProvider {

  private final VectorStore vectorStore;
  private final RagReadinessGate ragReadinessGate;
  private final RagProperties ragProperties;
  private final Map<String, String> cachedSceneLore = new ConcurrentHashMap<>();
  private Map<String, String> ragQueryByLang;

  @PostConstruct
  void init() throws IOException {
    final String prefix = "game_data/ondgard/";
    var resolver = new PathMatchingResourcePatternResolver();
    final Resource[] resources = resolver.getResources("classpath:game_data/ondgard/*/scene_rag_query.txt");
    ragQueryByLang = new HashMap<>();
    cachedSceneLore.clear();
    for( Resource resource : resources ){
      String uri = resource.getURI().toString();
      int prefixIdx = uri.indexOf(prefix);
      if( prefixIdx == -1 ) continue;
      String afterPrefix = uri.substring(prefixIdx + prefix.length());
      String lang = afterPrefix.substring(0, afterPrefix.indexOf('/'));
      ragQueryByLang.put(lang, resource.getContentAsString(StandardCharsets.UTF_8).trim());
      log.info("Scene RAG query loaded for lang={} ({} chars)", lang, ragQueryByLang.get(lang).length());
    }
  }

  public String getSceneLore(String loreLangCode) {
    String cached = cachedSceneLore.get(loreLangCode);
    if( cached != null ){
      return cached;
    }

    if( !ragReadinessGate.isReady() ){
      return "";
    }

    return cachedSceneLore.computeIfAbsent(loreLangCode, this::fetchSceneLore);
  }

  private String fetchSceneLore(String loreLangCode) {
    String query = ragQueryByLang.getOrDefault(loreLangCode, ragQueryByLang.values().iterator().next());
    try{
      SearchRequest searchRequest = SearchRequest.builder()
          .query(query)
          .topK(ragProperties.getSceneTopK())
          .similarityThreshold(ragProperties.getSceneSimilarityThreshold())
          .filterExpression("language == '" + loreLangCode + "'")
          .build();

      Collection<Document> results = vectorStore.similaritySearch(searchRequest);
      log.debug("SceneLoreProvider: retrieved {} chunks (lang={})", results.size(), loreLangCode);

      return results.stream()
          .map(Document::getText)
          .collect(Collectors.joining("\n\n---\n\n"));
    }catch(Exception ex){
      log.warn("SceneLoreProvider: RAG query failed (lang={}) — {}", loreLangCode, ex.getMessage());
      return "";
    }
  }
}
