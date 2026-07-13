package com.ondgard.game.chat.service.agent;

import com.ondgard.game.chat.model.agent.GmContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmAgent {

  @Qualifier( "gmModel" )
  private final ChatModel gmModel;

  private String instructions;

  @PostConstruct
  void loadSystemPrompt() throws IOException {
    var resource = new ClassPathResource("prompts/gm_system_prompt.md");
    instructions = resource.getContentAsString(StandardCharsets.UTF_8);

    log.info("GM instructions loaded ({} chars)", instructions.length());
  }

  public String generate(GmContext context) {
    String fullSystemPrompt = buildSystemPrompt(context);

    log.debug("Calling gmModel with user action ({} chars), system prompt ({} chars)", context.userAction().length(), fullSystemPrompt.length());
    try{
      String response = ChatClient.create(gmModel)
          .prompt().system(fullSystemPrompt).user(context.userAction())
          .call().content();
      log.debug("gmModel responded ({} chars)", response != null ? response.length() : 0);
      return response;
    }catch(Exception ex){
      log.error("gmModel call failed: {}", ex.getMessage(), ex);
      throw ex;
    }
  }

  public String regenerate(GmContext context, String previousDraft, String feedback) {
    String fullSystemPrompt = buildSystemPrompt(context);

    log.debug("Calling gmModel for regeneration with feedback ({} chars)", feedback.length());
    try{
      List<Message> messages = List.of(
          new SystemMessage(fullSystemPrompt),
          new UserMessage(context.userAction()),
          new AssistantMessage(previousDraft),
          new UserMessage(feedback)
      );

      var response = gmModel.call(new Prompt(messages));
      final String content = response.getResult().getOutput().getText();
      log.debug("gmModel regenerated ({} chars)", content != null ? content.length() : 0);
      return content;
    }catch(Exception ex){
      log.error("gmModel regeneration failed: {}", ex.getMessage(), ex);
      throw ex;
    }
  }

  // ************************************ private ************************************

  private String buildSystemPrompt(GmContext context) {
    String characterSection = GmPromptBuilder.buildCharacterSection(context.character());
    String campaignSection = GmPromptBuilder.buildCampaignSection(context.campaignContext());
    String advisorFacts = GmPromptBuilder.buildAdvisorFactsSection(context.campaignContext());
    String resolved = instructions
        .replace("{{language}}", context.language())
        .replace("{{advisorFacts}}", advisorFacts);
    return resolved + "\n\n---\n\n" + context.loreSection() + "\n\n---\n\n" + characterSection + campaignSection;
  }
}
