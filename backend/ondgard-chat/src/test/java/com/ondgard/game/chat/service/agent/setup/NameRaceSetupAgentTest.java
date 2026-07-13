package com.ondgard.game.chat.service.agent.setup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NameRaceSetupAgentTest {

  @Test
  void buildUserMessage_withSummary() {
    String result = NameRaceSetupAgent.buildUserMessage("Un guerriero errante", "Vuole essere un elfo");

    assertEquals("""
        ## Archetipo
        Un guerriero errante
        
        ## Preferenze giocatore
        Vuole essere un elfo
        
        Scegli razza e nome.""", result);
  }

  @Test
  void buildUserMessage_nullSummary() {
    String result = NameRaceSetupAgent.buildUserMessage("Un mago solitario", null);

    assertTrue(result.contains("## Archetipo\nUn mago solitario"));
    assertTrue(result.contains("## Preferenze giocatore\n(nessuna)"));
    assertTrue(result.endsWith("Scegli razza e nome."));
  }

  @Test
  void buildUserMessage_blankSummary() {
    String result = NameRaceSetupAgent.buildUserMessage("Un mercante", "   ");

    assertTrue(result.contains("(nessuna)"));
  }

  @Test
  void buildUserMessage_noLeadingSpaces() {
    String result = NameRaceSetupAgent.buildUserMessage("Archetipo", "Preferenze");

    for( String line : result.split("\n") ){
      assertFalse(line.startsWith(" "), "Line should not start with spaces: '" + line + "'");
    }
  }
}
