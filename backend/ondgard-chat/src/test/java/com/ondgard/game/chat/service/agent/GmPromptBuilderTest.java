package com.ondgard.game.chat.service.agent;

import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.GameScene;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GmPromptBuilderTest {

  @Test
  void buildCampaignSection_nullContext_returnsEmpty() {
    assertThat(GmPromptBuilder.buildCampaignSection(null)).isEmpty();
  }

  @Test
  void buildCampaignSection_allFieldsNull_returnsEmpty() {
    CampaignContext ctx = CampaignContext.builder().build();
    assertThat(GmPromptBuilder.buildCampaignSection(ctx)).isEmpty();
  }

  @Test
  void buildCampaignSection_onlyLocation_containsSceneSection() {
    CampaignContext ctx = CampaignContext.builder()
        .scene(GameScene.builder().currentLocation("Dark Forest").build())
        .build();

    String result = GmPromptBuilder.buildCampaignSection(ctx);

    assertThat(result).contains("SCENA CORRENTE");
    assertThat(result).contains("**Luogo:** Dark Forest");
    assertThat(result).doesNotContain("RIASSUNTO NARRATIVO");
    assertThat(result).doesNotContain("STORIA RECENTE");
  }

  @Test
  void buildCampaignSection_allSceneFields_containsAllLines() {
    CampaignContext ctx = CampaignContext.builder()
        .scene(GameScene.builder()
            .currentLocation("Village Square")
            .gameDate("12 Solara 1247")
            .gameTime("Dawn")
            .meteo("Sunny")
            .temperature("Warm")
            .build())
        .build();

    String result = GmPromptBuilder.buildCampaignSection(ctx);

    assertThat(result).contains("**Luogo:** Village Square");
    assertThat(result).contains("**Data:** 12 Solara 1247");
    assertThat(result).contains("**Ora:** Dawn");
    assertThat(result).contains("**Meteo:** Sunny");
    assertThat(result).contains("**Temperatura:** Warm");
  }

  @Test
  void buildCampaignSection_withNarrativeSummary() {
    CampaignContext ctx = CampaignContext.builder()
        .scene(GameScene.builder().currentLocation("Inn").build())
        .narrativeSummary("The hero arrived at the inn.")
        .build();

    String result = GmPromptBuilder.buildCampaignSection(ctx);

    assertThat(result).contains("RIASSUNTO NARRATIVO");
    assertThat(result).contains("The hero arrived at the inn.");
  }

  @Test
  void buildCampaignSection_blankSummary_omitsSection() {
    CampaignContext ctx = CampaignContext.builder()
        .scene(GameScene.builder().currentLocation("Inn").build())
        .narrativeSummary("   ")
        .build();

    String result = GmPromptBuilder.buildCampaignSection(ctx);

    assertThat(result).doesNotContain("RIASSUNTO NARRATIVO");
  }

  @Test
  void buildCampaignSection_withRecentHistory() {
    CampaignContext ctx = CampaignContext.builder()
        .scene(GameScene.builder().currentLocation("Arena").build())
        .recentHistory(List.of(
            new ChatEntry(1, "I look around", "You see a dark arena"),
            new ChatEntry(2, "I draw my sword", "The crowd cheers")
        ))
        .build();

    String result = GmPromptBuilder.buildCampaignSection(ctx);

    assertThat(result).contains("STORIA RECENTE");
    assertThat(result).contains("Turno 1");
    assertThat(result).contains("Turno 2");
  }

  @Test
  void buildCampaignSection_emptyHistory_omitsSection() {
    CampaignContext ctx = CampaignContext.builder()
        .scene(GameScene.builder().currentLocation("Inn").build())
        .recentHistory(Collections.emptyList())
        .build();

    String result = GmPromptBuilder.buildCampaignSection(ctx);

    assertThat(result).doesNotContain("STORIA RECENTE");
  }

  @Test
  void buildCampaignSection_fullContext_allSectionsPresent() {
    CampaignContext ctx = CampaignContext.builder()
        .scene(GameScene.builder()
            .currentLocation("Castle")
            .gameDate("1 Luna 1248")
            .gameTime("Noon")
            .meteo("Cloudy")
            .temperature("Mild")
            .build())
        .narrativeSummary("The kingdom faces a new threat.")
        .recentHistory(List.of(
            new ChatEntry(1, "I enter the castle", "Guards salute you"),
            new ChatEntry(2, "I ask about the king", "He waits in the throne room")
        ))
        .build();

    String result = GmPromptBuilder.buildCampaignSection(ctx);

    assertThat(result).contains("SCENA CORRENTE");
    assertThat(result).contains("RIASSUNTO NARRATIVO");
    assertThat(result).contains("STORIA RECENTE");

    int sceneIdx = result.indexOf("SCENA CORRENTE");
    int summaryIdx = result.indexOf("RIASSUNTO NARRATIVO");
    int historyIdx = result.indexOf("STORIA RECENTE");
    assertThat(sceneIdx).isLessThan(summaryIdx);
    assertThat(summaryIdx).isLessThan(historyIdx);
  }

  @Test
  void buildCampaignSection_withRawSummaryBuffer_containsEventiRecentiSection() {
    CampaignContext ctx = CampaignContext.builder()
        .scene(GameScene.builder().currentLocation("Forest").build())
        .rawSummaryBuffer(List.of(new ChatEntry(1, "action", "response")))
        .build();

    String result = GmPromptBuilder.buildCampaignSection(ctx);

    assertThat(result).contains("EVENTI RECENTI NON RIASSUNTI");
    assertThat(result).contains("Turno 1:");
    assertThat(result).contains("- Giocatore: action");
  }

  @Test
  void buildCampaignSection_emptyRawBuffer_omitsSection() {
    CampaignContext ctx = CampaignContext.builder()
        .scene(GameScene.builder().currentLocation("Inn").build())
        .rawSummaryBuffer(Collections.emptyList())
        .build();

    String result = GmPromptBuilder.buildCampaignSection(ctx);

    assertThat(result).doesNotContain("EVENTI RECENTI NON RIASSUNTI");
  }

  @Test
  void buildCampaignSection_nullRawBuffer_omitsSection() {
    CampaignContext ctx = CampaignContext.builder()
        .scene(GameScene.builder().currentLocation("Inn").build())
        .rawSummaryBuffer(null)
        .build();

    String result = GmPromptBuilder.buildCampaignSection(ctx);

    assertThat(result).doesNotContain("EVENTI RECENTI NON RIASSUNTI");
  }

  // ************************* buildAdvisorFactsSection tests *************************

  @Test
  void buildAdvisorFactsSection_nullContext_returnsEmpty() {
    assertThat(GmPromptBuilder.buildAdvisorFactsSection(null)).isEmpty();
  }

  @Test
  void buildAdvisorFactsSection_emptyFacts_returnsEmpty() {
    CampaignContext ctx = CampaignContext.builder().build();
    assertThat(GmPromptBuilder.buildAdvisorFactsSection(ctx)).isEmpty();
  }

  @Test
  void buildAdvisorFactsSection_withFacts_containsHeadingAndBullets() {
    CampaignContext ctx = CampaignContext.builder().build();
    ctx.getAdvisorFacts().add("Il taverniere si chiama Gundren");
    ctx.getAdvisorFacts().add("La spada e' di fattura nanica");

    String result = GmPromptBuilder.buildAdvisorFactsSection(ctx);

    assertThat(result).contains("FATTI STABILITI DALL'ADVISOR");
    assertThat(result).contains("- Il taverniere si chiama Gundren");
    assertThat(result).contains("- La spada e' di fattura nanica");
  }

  // ************************* rawSummaryBuffer ordering tests *************************

  @Test
  void buildCampaignSection_rawBufferOrdering_betweenSummaryAndHistory() {
    CampaignContext ctx = CampaignContext.builder()
        .scene(GameScene.builder().currentLocation("Castle").build())
        .narrativeSummary("A hero arose.")
        .rawSummaryBuffer(List.of(new ChatEntry(5, "explore", "found treasure")))
        .recentHistory(List.of(
            new ChatEntry(10, "I fight", "You win")
        ))
        .build();

    String result = GmPromptBuilder.buildCampaignSection(ctx);

    int summaryIdx = result.indexOf("RIASSUNTO NARRATIVO");
    int bufferIdx = result.indexOf("EVENTI RECENTI NON RIASSUNTI");
    int historyIdx = result.indexOf("STORIA RECENTE");

    assertThat(summaryIdx).isLessThan(bufferIdx);
    assertThat(bufferIdx).isLessThan(historyIdx);
  }
}
