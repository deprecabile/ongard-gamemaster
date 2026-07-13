package com.ondgard.game.chat.service.agent.advisor;

import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.CampaignQuestLog;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.GameScene;
import com.ondgard.game.chat.model.inventory.Inventory;
import com.ondgard.game.chat.service.rag.LoreRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith( MockitoExtension.class )
class AdvisorToolsTest {

  @Mock
  private LoreRetrievalService loreRetrievalService;

  private CampaignContext campaignContext;
  private AdvisorTools advisorTools;

  @BeforeEach
  void setUp() {
    campaignContext = CampaignContext.builder()
        .scene(GameScene.builder()
            .currentLocation("Taverna del Cinghiale")
            .gameTime("Sera")
            .meteo("Pioggia")
            .build())
        .inventory(Inventory.empty())
        .questLog(new CampaignQuestLog(3, "Trovare la spada perduta", "Parlato col fabbro"))
        .narrativeSummary("L'eroe e' arrivato al villaggio dopo un lungo viaggio.")
        .recentHistory(new ArrayList<>(List.of(
            new ChatEntry(1, "Entro nella taverna", "La porta cigola mentre entri."),
            new ChatEntry(2, "Parlo col taverniere", "Un uomo robusto ti guarda.")
        )))
        .rawSummaryBuffer(new ArrayList<>(List.of(
            new ChatEntry(0, "Inizio avventura", "Ti risvegli in un campo.")
        )))
        .build();

    advisorTools = new AdvisorTools(loreRetrievalService, campaignContext, "it");
  }

  // ************************* searchLore *************************

  @Test
  void searchLore_delegatesToLoreRetrievalService() {
    when(loreRetrievalService.retrieveForAdvisor("it", "nani artigiani"))
        .thenReturn("[RAZZE]\nI nani sono abili artigiani.");

    String result = advisorTools.searchLore("nani artigiani");

    assertThat(result).contains("I nani sono abili artigiani");
    verify(loreRetrievalService).retrieveForAdvisor("it", "nani artigiani");
  }

  @Test
  void searchLore_returnsValidJson() {
    when(loreRetrievalService.retrieveForAdvisor("it", "nani"))
        .thenReturn("[RAZZE]\nI nani sono forti.");

    String result = advisorTools.searchLore("nani");

    assertThat(result).startsWith("{").endsWith("}");
    assertThat(result).contains("\"lore\"");
  }

  @Test
  void searchLore_usesConfiguredLoreLangCode() {
    var enTools = new AdvisorTools(loreRetrievalService, campaignContext, "en");
    when(loreRetrievalService.retrieveForAdvisor("en", "dwarves")).thenReturn("");

    enTools.searchLore("dwarves");

    verify(loreRetrievalService).retrieveForAdvisor("en", "dwarves");
  }

  // ************************* getCurrentScene *************************

  @Test
  void getCurrentScene_containsSceneInventoryAndQuests() {
    String result = advisorTools.getCurrentScene();

    assertThat(result).startsWith("{").endsWith("}");
    assertThat(result).contains("Taverna del Cinghiale");
    assertThat(result).contains("\"inventory\"");
    assertThat(result).contains("Trovare la spada perduta");
  }

  @Test
  void getCurrentScene_nullScene_omitsSceneKey() {
    campaignContext.setScene(null);

    String result = advisorTools.getCurrentScene();

    assertThat(result).doesNotContain("\"scene\"");
    assertThat(result).contains("\"inventory\"");
  }

  @Test
  void getCurrentScene_blankQuestActive_omitsQuestActiveKey() {
    campaignContext.setQuestLog(new CampaignQuestLog(3, "  ", "Done"));

    String result = advisorTools.getCurrentScene();

    assertThat(result).doesNotContain("\"questActive\"");
  }

  // ************************* getRecentHistory *************************

  @Test
  void getRecentHistory_returnsJsonWithFormattedTurns() {
    String result = advisorTools.getRecentHistory();

    assertThat(result).startsWith("{").endsWith("}");
    assertThat(result).contains("\"history\"");
    assertThat(result).contains("Turno 1:");
    assertThat(result).contains("Entro nella taverna");
    assertThat(result).contains("Turno 2:");
  }

  @Test
  void getRecentHistory_emptyHistory_returnsJsonWithEmptyValue() {
    campaignContext.setRecentHistory(List.of());

    String result = advisorTools.getRecentHistory();

    assertThat(result).startsWith("{").endsWith("}");
    assertThat(result).contains("\"history\"");
  }

  // ************************* searchPastEvents *************************

  @Test
  void searchPastEvents_returnsSummaryAndRawBuffer() {
    String result = advisorTools.searchPastEvents("viaggio");

    assertThat(result).startsWith("{").endsWith("}");
    assertThat(result).contains("\"narrativeSummary\"");
    assertThat(result).contains("lungo viaggio");
    assertThat(result).contains("\"recentUncompressedTurns\"");
    assertThat(result).contains("Ti risvegli in un campo.");
  }

  @Test
  void searchPastEvents_noSummaryNoBuffer_returnsEmptyJsonObject() {
    campaignContext.setNarrativeSummary(null);
    campaignContext.setRawSummaryBuffer(List.of());

    String result = advisorTools.searchPastEvents("qualcosa");

    assertThat(result).isEqualTo("{}");
  }

  // ************************* registerAdvisorFact *************************

  @Test
  void registerAdvisorFact_addsToList() {
    advisorTools.registerAdvisorFact("Il taverniere si chiama Gundren");

    assertThat(advisorTools.getAdvisorFacts()).containsExactly("Il taverniere si chiama Gundren");
  }

  @Test
  void registerAdvisorFact_multipleFacts_preservesOrder() {
    advisorTools.registerAdvisorFact("Fatto 1");
    advisorTools.registerAdvisorFact("Fatto 2");
    advisorTools.registerAdvisorFact("Fatto 3");

    assertThat(advisorTools.getAdvisorFacts()).containsExactly("Fatto 1", "Fatto 2", "Fatto 3");
  }

  @Test
  void registerAdvisorFact_returnsValidJson() {
    String result = advisorTools.registerAdvisorFact("Un fatto");

    assertThat(result).startsWith("{").endsWith("}");
    assertThat(result).contains("\"status\"");
    assertThat(result).contains("registered");
  }

  @Test
  void getAdvisorFacts_initiallyEmpty() {
    assertThat(new AdvisorTools(loreRetrievalService, campaignContext, "it").getAdvisorFacts()).isEmpty();
  }
}
