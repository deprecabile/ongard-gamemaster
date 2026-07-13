package com.ondgard.game.chat.service.rag;

import com.ondgard.game.chat.config.ai.RagProperties;
import com.ondgard.game.chat.model.GameScene;
import com.ondgard.game.chat.service.agent.RagQueryAgent;
import com.ondgard.game.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith( MockitoExtension.class )
class LoreRetrievalServiceImplTest {

  @Mock
  private VectorStore vectorStore;

  @Mock
  private RagQueryAgent ragQueryAgent;

  @Captor
  private ArgumentCaptor<SearchRequest> searchRequestCaptor;

  private RagProperties ragProperties;
  private RagReadinessGate ragReadinessGate;
  private LoreRetrievalServiceImpl service;

  @BeforeEach
  void setUp() {
    ragProperties = new RagProperties();
    ragReadinessGate = new RagReadinessGate();
    ragReadinessGate.markReady();
    lenient().when(ragQueryAgent.composeQueries(any(), any(), any(), any(), any())).thenReturn(List.of());
    service = new LoreRetrievalServiceImpl(vectorStore, ragProperties, ragReadinessGate, ragQueryAgent);
  }

  @Test
  void retrieve_throwsServiceUnavailable_whenRagNotReady() {
    var notReadyGate = new RagReadinessGate();
    var notReadyService = new LoreRetrievalServiceImpl(vectorStore, ragProperties, notReadyGate, ragQueryAgent);

    assertThatThrownBy(() -> notReadyService.retrieve("it", "action", null, null, null))
        .isInstanceOf(ServiceUnavailableException.class);

    verifyNoInteractions(vectorStore);
  }

  @Test
  void retrieve_composesQueryWithSceneContext() {
    var scene = GameScene.builder()
        .currentLocation("Dark Forest")
        .meteo("Rainy")
        .gameTime("Night")
        .build();
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    service.retrieve("it", "I look around", scene, null, null);

    verify(vectorStore).similaritySearch(searchRequestCaptor.capture());
    String query = searchRequestCaptor.getValue().getQuery();

    assertThat(query).startsWith("Instruct:");
    assertThat(query).contains("Recupera lore");
    assertThat(query).contains("Query:");
    assertThat(query).contains("I look around");
    assertThat(query).contains("Luogo: Dark Forest");
    assertThat(query).contains("Meteo: Rainy");
    assertThat(query).contains("Ora: Night");
  }

  @Test
  void retrieve_usesEnglishLabels_whenLoreLangIsEn() {
    var scene = GameScene.builder()
        .currentLocation("Dark Forest")
        .meteo("Rainy")
        .gameTime("Night")
        .build();
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    service.retrieve("en", "I look around", scene, null, null);

    verify(vectorStore).similaritySearch(searchRequestCaptor.capture());
    String query = searchRequestCaptor.getValue().getQuery();

    assertThat(query).contains("Retrieve lore and world information");
    assertThat(query).contains("Location: Dark Forest");
    assertThat(query).contains("Weather: Rainy");
    assertThat(query).contains("Time: Night");
  }

  @Test
  void retrieve_usesRetrievalTopKAndThresholdFromProperties() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    service.retrieve("it", "action", null, null, null);

    verify(vectorStore).similaritySearch(searchRequestCaptor.capture());
    SearchRequest request = searchRequestCaptor.getValue();

    assertThat(request.getTopK()).isEqualTo(ragProperties.getRetrievalTopK());
    assertThat(request.getSimilarityThreshold()).isEqualTo(ragProperties.getRetrievalSimilarityThreshold());
  }

  @Test
  void retrieve_setsLanguageFilter() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    service.retrieve("it", "action", null, null, null);

    verify(vectorStore).similaritySearch(searchRequestCaptor.capture());
    SearchRequest request = searchRequestCaptor.getValue();

    assertThat(request.getFilterExpression()).isNotNull();
    assertThat(request.getFilterExpression().toString()).contains("language");
    assertThat(request.getFilterExpression().toString()).contains("it");
  }

  @Test
  void retrieve_formatsResultsWithCategoryLabels() {
    List<Document> results = List.of(
        new Document("Mountains and valleys", Map.of("category", "GEOGRAFIA")),
        new Document("The ancient war", Map.of("category", "STORIA"))
    );
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(results);

    String result = service.retrieve("it", "Tell me about the land", null, null, null);

    assertThat(result).isEqualTo(
        "[GEOGRAFIA]\nMountains and valleys\n\n---\n\n[STORIA]\nThe ancient war"
    );
  }

  @Test
  void retrieve_returnsEmptyString_whenNoResults() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    String result = service.retrieve("it", "action", null, null, null);

    assertThat(result).isEmpty();
  }

  @Test
  void retrieve_handlesNullScene() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    service.retrieve("it", "I open the chest", null, null, null);

    verify(vectorStore).similaritySearch(searchRequestCaptor.capture());
    String query = searchRequestCaptor.getValue().getQuery();

    assertThat(query).contains("I open the chest");
    assertThat(query).doesNotContain("Luogo:");
    assertThat(query).doesNotContain("Meteo:");
    assertThat(query).doesNotContain("Ora:");
  }

  @Test
  void retrieve_handlesPartialScene() {
    var scene = GameScene.builder()
        .currentLocation("Village Square")
        .build();
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    service.retrieve("it", "I walk forward", scene, null, null);

    verify(vectorStore).similaritySearch(searchRequestCaptor.capture());
    String query = searchRequestCaptor.getValue().getQuery();

    assertThat(query).contains("Luogo: Village Square");
    assertThat(query).doesNotContain("Meteo:");
    assertThat(query).doesNotContain("Ora:");
  }

  @Test
  void retrieve_usesMultiQuery_whenAgentReturnsQueries() {
    when(ragQueryAgent.composeQueries(any(), any(), any(), any(), any()))
        .thenReturn(List.of("foresta oscura", "tribù elfiche"));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    service.retrieve("it", "Entro nella foresta", null, null, null);

    verify(vectorStore, times(2)).similaritySearch(any(SearchRequest.class));
  }

  @Test
  void retrieve_fallsBackToSimpleQuery_whenAgentReturnsEmpty() {
    when(ragQueryAgent.composeQueries(any(), any(), any(), any(), any())).thenReturn(List.of());
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    service.retrieve("it", "action", null, null, null);

    verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
  }

  @Test
  void retrieve_deduplicatesAcrossQueries() {
    String sharedDocId = "shared-doc-1";
    Document docLowScore = Document.builder()
        .id(sharedDocId).text("Lore chunk A").metadata("category", "GEOGRAFIA").score(0.7).build();
    Document docHighScore = Document.builder()
        .id(sharedDocId).text("Lore chunk A").metadata("category", "GEOGRAFIA").score(0.9).build();
    Document uniqueDoc = Document.builder()
        .id("unique-doc-2").text("Lore chunk B").metadata("category", "STORIA").score(0.8).build();

    when(ragQueryAgent.composeQueries(any(), any(), any(), any(), any()))
        .thenReturn(List.of("query1", "query2"));
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(docLowScore))
        .thenReturn(List.of(docHighScore, uniqueDoc));

    String result = service.retrieve("it", "action", null, null, null);

    // Should contain both chunks but only once for the shared doc (highest score wins)
    assertThat(result).contains("[GEOGRAFIA]");
    assertThat(result).contains("[STORIA]");
    // "---" separator appears once between 2 results
    assertThat(result.split("---")).hasSize(2);
  }

  // ************************* retrieveForAdvisor tests *************************

  @Test
  void retrieveForAdvisor_returnsEmpty_whenRagNotReady() {
    var notReadyGate = new RagReadinessGate();
    var notReadyService = new LoreRetrievalServiceImpl(vectorStore, ragProperties, notReadyGate, ragQueryAgent);

    String result = notReadyService.retrieveForAdvisor("it", "nani artigiani");

    assertThat(result).isEmpty();
    verifyNoInteractions(vectorStore);
  }

  @Test
  void retrieveForAdvisor_delegatesToExecuteSearchAndFormat() {
    List<Document> results = List.of(
        new Document("I nani sono abili artigiani", Map.of("category", "RAZZE"))
    );
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(results);

    String result = service.retrieveForAdvisor("it", "nani artigiani");

    assertThat(result).contains("[RAZZE]");
    assertThat(result).contains("I nani sono abili artigiani");

    verify(vectorStore).similaritySearch(searchRequestCaptor.capture());
    SearchRequest request = searchRequestCaptor.getValue();
    assertThat(request.getQuery()).isEqualTo("nani artigiani");
    assertThat(request.getFilterExpression().toString()).contains("it");
  }

  @Test
  void retrieveForAdvisor_returnsEmpty_whenNoResults() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    String result = service.retrieveForAdvisor("en", "dragons");

    assertThat(result).isEmpty();
  }

  // ************************* retrieve — other tests *************************

  @Test
  void retrieve_passesDisplayNameToRagQueryAgent() {
    when(ragQueryAgent.composeQueries(any(), any(), any(), any(), any())).thenReturn(List.of());
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    service.retrieve("it", "action", null, null, null);

    verify(ragQueryAgent).composeQueries(eq("italiano"), eq("action"), any(), any(), any());
  }
}
