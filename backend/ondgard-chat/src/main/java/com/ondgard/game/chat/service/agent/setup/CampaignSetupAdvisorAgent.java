package com.ondgard.game.chat.service.agent.setup;

import com.ondgard.game.chat.model.ChatEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
public class CampaignSetupAdvisorAgent {

  private final ChatModel model;
  private final String systemPrompt;
  private final CampaignSetupAdvisorTools tools;

  CampaignSetupAdvisorAgent(ChatModel model, CampaignSetupAdvisorTools tools, String systemPrompt) {
    this.model = model;
    this.systemPrompt = systemPrompt;
    this.tools = tools;
  }

  public String chat(Collection<ChatEntry> history, String userMessage) {
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(systemPrompt));

    for( ChatEntry entry : history ){
      messages.add(new UserMessage(entry.userMessage()));
      messages.add(new AssistantMessage(entry.gmResponse()));
    }
    messages.add(new UserMessage(userMessage));

    log.debug("Calling setupAdvisorModel with {} history exchanges, message ({} chars)",
        history.size(), userMessage.length());
    try{
      String content = ChatClient.create(model)
          .prompt(new Prompt(messages))
          .tools(tools)
          .call()
          .content();
      log.debug("setupAdvisorModel responded ({} chars)", content != null ? content.length() : 0);
      return content;
    }catch(Exception ex){
      log.error("setupAdvisorModel call failed: {}", ex.getMessage(), ex);
      throw ex;
    }
  }
}
