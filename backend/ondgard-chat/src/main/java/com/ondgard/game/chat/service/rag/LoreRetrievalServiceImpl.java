package com.ondgard.game.chat.service.rag;

import com.ondgard.game.chat.config.ai.RagProperties;
import com.ondgard.game.chat.error.GameErrorCode;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.GameScene;
import com.ondgard.game.chat.service.agent.RagQueryAgent;
import com.ondgard.game.chat.util.LanguageUtils;
import com.ondgard.game.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoreRetrievalServiceImpl implements LoreRetrievalService {

  private final VectorStore vectorStore;
  private final RagProperties ragProperties;
  private final RagReadinessGate ragReadinessGate;
  private final RagQueryAgent ragQueryAgent;

  @Override
  public String retrieve(String loreLangCode, String userAction, GameScene scene, String questActive, ChatEntry lastTurn) {
    if( !ragReadinessGate.isReady() ){
      throw new ServiceUnavailableException(GameErrorCode.RAG_NOT_READY.getCode(), "Il mondo di Ondgard si sta materializzando...");
    }

    String loreLangDisplay = LanguageUtils.toDisplayName(loreLangCode);
    List<String> queries = ragQueryAgent.composeQueries(loreLangDisplay, userAction, scene, questActive, lastTurn);

    List<Document> results;
    if( queries.isEmpty() ){
      String query = composeQuery(loreLangCode, userAction, scene);
      log.debug("RAG fallback query composed ({} chars): {}", query.length(), query);
      results = executeSearch(query, loreLangCode);
    } else{
      results = executeMultiQuerySearch(queries, loreLangCode);
    }

    return formatResults(results);
  }

  @Override
  public String retrieveForAdvisor(String loreLangCode, String query) {
    if( !ragReadinessGate.isReady() ){
      return "";
    }
    List<Document> results = executeSearch(query, loreLangCode);
    return formatResults(results);
  }

  // ************************************ private ************************************

  private List<Document> executeSearch(String query, String loreLangCode) {
    SearchRequest searchRequest = SearchRequest.builder()
        .query(query)
        .topK(ragProperties.getRetrievalTopK())
        .similarityThreshold(ragProperties.getRetrievalSimilarityThreshold())
        .filterExpression("language == '" + loreLangCode + "'")
        .build();

    return vectorStore.similaritySearch(searchRequest);
  }

  private List<Document> executeMultiQuerySearch(List<String> queries, String loreLangCode) {
    List<CompletableFuture<List<Document>>> futures = queries.stream()
        .map(q -> CompletableFuture.supplyAsync(() -> executeSearch(q, loreLangCode)))
        .toList();

    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

    // Deduplicate by document ID, keeping the highest score
    var deduped = new LinkedHashMap<String, Document>();
    for( var future : futures ){
      for( Document doc : future.join() ){
        deduped.merge(doc.getId(), doc, (existing, incoming) -> {
          Double existingScore = existing.getScore();
          Double incomingScore = incoming.getScore();
          if( existingScore == null ) return incoming;
          if( incomingScore == null ) return existing;
          return incomingScore > existingScore ? incoming : existing;
        });
      }
    }

    return deduped.values().stream()
        .sorted((a, b) -> {
          Double scoreA = a.getScore();
          Double scoreB = b.getScore();
          if( scoreA == null && scoreB == null ) return 0;
          if( scoreA == null ) return 1;
          if( scoreB == null ) return -1;
          return Double.compare(scoreB, scoreA);
        })
        .limit(ragProperties.getRetrievalTopK())
        .toList();
  }

  private String composeQuery(String loreLangCode, String userAction, GameScene scene) {
    var body = new StringBuilder(userAction);
    boolean isItalian = "it".equals(loreLangCode);

    if( scene != null ){
      if( scene.getCurrentLocation() != null ){
        body.append(isItalian ? " Luogo: " : " Location: ").append(scene.getCurrentLocation());
      }
      if( scene.getMeteo() != null ){
        body.append(isItalian ? " Meteo: " : " Weather: ").append(scene.getMeteo());
      }
      if( scene.getGameTime() != null ){
        body.append(isItalian ? " Ora: " : " Time: ").append(scene.getGameTime());
      }
    }

    String instruct = isItalian
        ? "Instruct: Recupera lore e informazioni sul mondo di gioco pertinenti a questa azione"
        : "Instruct: Retrieve lore and world information relevant to this action";

    return instruct + "\nQuery: " + body;
  }

  private String formatResults(List<Document> results) {
    if( results.isEmpty() ){
      return "";
    }

    return results.stream()
        .map(doc -> {
          String category = (String) doc.getMetadata().getOrDefault("category", "GENERICO");
          return "[" + category + "]\n" + doc.getText();
        })
        .collect(Collectors.joining("\n\n---\n\n"));
  }
}
