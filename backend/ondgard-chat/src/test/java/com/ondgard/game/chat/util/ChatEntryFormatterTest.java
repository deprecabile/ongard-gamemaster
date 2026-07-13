package com.ondgard.game.chat.util;

import com.ondgard.game.chat.model.ChatEntry;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatEntryFormatterTest {

  @Test
  void toTextBuffer_multipleEntries_formatsInOrder() {
    var entries = List.of(
        new ChatEntry(1, "explore", "found cave"),
        new ChatEntry(2, "enter cave", "darkness surrounds you")
    );

    String result = ChatEntryFormatter.toTextBuffer(entries);

    assertThat(result).isEqualTo("""
        Turno 1:
        - Giocatore: explore
        - GM: found cave
        
        Turno 2:
        - Giocatore: enter cave
        - GM: darkness surrounds you
        
        """.stripIndent());
  }

  @Test
  void toTextBuffer_emptyList_returnsEmpty() {
    assertThat(ChatEntryFormatter.toTextBuffer(Collections.emptyList())).isEmpty();
  }

  @Test
  void toTextBuffer_null_returnsEmpty() {
    assertThat(ChatEntryFormatter.toTextBuffer(null)).isEmpty();
  }

  @Test
  void toTextBuffer_singleEntry_formatsCorrectly() {
    var entries = List.of(new ChatEntry(5, "attack", "you hit the goblin"));

    String result = ChatEntryFormatter.toTextBuffer(entries);

    assertThat(result).startsWith("Turno 5:");
    assertThat(result).contains("- Giocatore: attack");
    assertThat(result).contains("- GM: you hit the goblin");
    assertThat(result).endsWith("\n\n");
  }
}
