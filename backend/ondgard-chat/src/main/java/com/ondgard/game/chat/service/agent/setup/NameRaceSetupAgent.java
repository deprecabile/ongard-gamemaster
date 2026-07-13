package com.ondgard.game.chat.service.agent.setup;

import com.google.gson.Gson;
import com.ondgard.game.GameGsonFactory;
import com.ondgard.game.chat.model.agent.NameRaceSetupResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

@Slf4j
public class NameRaceSetupAgent {

  private static final Gson GSON = GameGsonFactory.build();

  private final ChatModel model;
  private final CampaignSetupGeneratorTools tools;
  private final String systemPrompt;

  NameRaceSetupAgent(ChatModel model, CampaignSetupGeneratorTools tools, String systemPrompt) {
    this.model = model;
    this.tools = tools;
    this.systemPrompt = systemPrompt;
  }

  public NameRaceSetupResponse pick(String archetypeDescription, String summary) {
    String userMessage = buildUserMessage(archetypeDescription, summary);
    log.debug("Calling nameRaceSetupModel ({} chars user message)", userMessage.length());

    try{
      String json = ChatClient.create(model)
          .prompt()
          .system(systemPrompt)
          .user(userMessage)
          .tools(tools)
          .call()
          .content();

      return GSON.fromJson(json, NameRaceSetupResponse.class);
    }catch(Exception ex){
      log.error("nameRaceSetupModel call failed: {}", ex.getMessage(), ex);
      throw ex;
    }
  }

  static String buildUserMessage(String archetypeDescription, String summary) {
    String preferences = summary != null && !summary.isBlank() ? summary : "(nessuna)";
    return """
        ## Archetipo
        %s
        
        ## Preferenze giocatore
        %s
        
        Scegli razza e nome.""".formatted(archetypeDescription, preferences);
  }
}
