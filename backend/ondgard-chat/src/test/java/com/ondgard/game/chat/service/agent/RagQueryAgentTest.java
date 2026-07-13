package com.ondgard.game.chat.service.agent;

import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.GameScene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith( MockitoExtension.class )
class RagQueryAgentTest {

  @Mock
  private ChatModel ragQueryModel;

  private RagQueryAgent agent;

  @BeforeEach
  void setUp() throws Exception {
    agent = new RagQueryAgent(ragQueryModel);
    agent.loadTemplate();
  }

  @Test
  void buildUserMessage_withAllFields() {
    var scene = GameScene.builder()
        .currentLocation("Foresta Oscura")
        .gameDate("12 Invernal")
        .gameTime("Notte")
        .meteo("Pioggia")
        .build();
    var lastTurn = new ChatEntry(3, "Cerco tracce", "Trovi impronte fresche nel fango");

    String msg = agent.buildUserMessage("Seguo le tracce", scene, "Trovare il bandito", lastTurn);

    assertThat(msg).contains("## Azione\nSeguo le tracce");
    assertThat(msg).contains("## Scena");
    assertThat(msg).contains("- Luogo: Foresta Oscura");
    assertThat(msg).contains("- Data: 12 Invernal");
    assertThat(msg).contains("- Ora: Notte");
    assertThat(msg).contains("- Meteo: Pioggia");
    assertThat(msg).contains("## Quest Attive\nTrovare il bandito");
    assertThat(msg).contains("## Ultimo Turno");
    assertThat(msg).contains("- Giocatore: Cerco tracce");
    assertThat(msg).contains("- GM: Trovi impronte fresche nel fango");
  }

  @Test
  void buildUserMessage_withNullScene() {
    String msg = agent.buildUserMessage("Guardo intorno", null, null, null);

    assertThat(msg).contains("## Azione\nGuardo intorno");
    assertThat(msg).doesNotContain("## Scena");
    assertThat(msg).doesNotContain("## Quest Attive");
    assertThat(msg).doesNotContain("## Ultimo Turno");
  }

  @Test
  void buildUserMessage_withPartialScene() {
    var scene = GameScene.builder()
        .currentLocation("Taverna")
        .build();

    String msg = agent.buildUserMessage("Parlo con l'oste", scene, null, null);

    assertThat(msg).contains("## Scena");
    assertThat(msg).contains("- Luogo: Taverna");
    assertThat(msg).doesNotContain("- Data:");
    assertThat(msg).doesNotContain("- Ora:");
    assertThat(msg).doesNotContain("- Meteo:");
  }

  @Test
  void buildUserMessage_withBlankQuestActive() {
    String msg = agent.buildUserMessage("Azione", null, "  ", null);

    assertThat(msg).doesNotContain("## Quest Attive");
  }

  @Test
  void composeQueries_returnsEmptyList_whenModelThrows() {
    when(ragQueryModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenThrow(new RuntimeException("LLM unavailable"));

    List<String> result = agent.composeQueries("italiano", "Entro nella grotta", null, null, null);

    assertThat(result).isEmpty();
  }
}
