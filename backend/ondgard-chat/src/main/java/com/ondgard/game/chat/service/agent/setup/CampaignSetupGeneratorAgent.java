package com.ondgard.game.chat.service.agent.setup;

import com.ondgard.game.chat.model.setup.CampaignArchetype;
import com.ondgard.game.chat.model.setup.SetupGenerateMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

@Slf4j
public class CampaignSetupGeneratorAgent {

  private final ChatModel model;
  private final CampaignSetupGeneratorTools tools;
  private final String characterPromptTemplate;
  private final String scenePromptTemplate;

  CampaignSetupGeneratorAgent(ChatModel model,
                              CampaignSetupGeneratorTools tools,
                              String characterPromptTemplate,
                              String scenePromptTemplate) {
    this.model = model;
    this.tools = tools;
    this.characterPromptTemplate = characterPromptTemplate;
    this.scenePromptTemplate = scenePromptTemplate;
  }

  public String generate(SetupGenerateMode mode,
                         CampaignArchetype archetype,
                         String summary,
                         String characterPrompt,
                         String raceName,
                         String characterName) {
    if( mode == SetupGenerateMode.FULL ){
      throw new IllegalArgumentException("FULL mode must be orchestrated by the service layer");
    }
    if( mode == SetupGenerateMode.SCENE && (characterPrompt == null || characterPrompt.isBlank()) ){
      throw new IllegalArgumentException("characterPrompt is required for SCENE mode");
    }

    final String systemPrompt = mode == SetupGenerateMode.CHARACTER
        ? characterPromptTemplate
        : scenePromptTemplate;

    final String userMessage = buildUserMessage(mode, archetype, summary, characterPrompt, raceName, characterName);
    log.debug("Calling setupGeneratorModel in {} mode, archetype={} ({} chars user message)", mode, archetype.code(), userMessage.length());

    try{
      return ChatClient.create(model)
          .prompt()
          .system(systemPrompt)
          .user(userMessage)
          .tools(tools)
          .call()
          .content();
    }catch(Exception ex){
      log.error("setupGeneratorModel call failed in {} mode: {}", mode, ex.getMessage(), ex);
      throw ex;
    }
  }

  private String buildUserMessage(SetupGenerateMode mode,
                                  CampaignArchetype archetype,
                                  String summary,
                                  String characterPrompt,
                                  String raceName,
                                  String characterName) {
    String inventoryHints = String.join(", ", archetype.inventoryHints());
    String summaryText = summary != null && !summary.isBlank() ? summary : "(nessuna)";

    var sb = new StringBuilder();

    if( characterName != null && !characterName.isBlank() ){
      sb.append("## Nome e razza\n").append(characterName);
      if( raceName != null && !raceName.isBlank() ){
        sb.append(", ").append(raceName);
      }
      sb.append("\n\n");
    }

    sb.append("## Archetipo\n").append(archetype.description()).append("\n\n");
    sb.append("## Oggetti iniziali\n").append(inventoryHints).append("\n\n");

    if( mode == SetupGenerateMode.SCENE ){
      sb.append("## Direttive creative\n").append(archetype.theme()).append("\n\n");
      sb.append("## Prompt personaggio\n").append(characterPrompt).append("\n\n");
    }

    sb.append("## Preferenze giocatore\n").append(summaryText).append("\n\n");
    sb.append("Genera.");

    return sb.toString();
  }
}
