package com.ondgard.game.chat.service.agent.setup;

import com.ondgard.game.chat.model.ChatEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Collection;

@Slf4j
public class CampaignSetupSummaryAgent {

  private final ChatModel model;
  private final String systemPrompt;

  CampaignSetupSummaryAgent(ChatModel model, String systemPrompt) {
    this.model = model;
    this.systemPrompt = systemPrompt;
  }

  public String summarize(Collection<ChatEntry> history) {
    if( history == null || history.isEmpty() ){
      log.debug("No history provided, returning null");
      return null;
    }

    String formattedHistory = formatHistory(history);
    log.debug("Calling setupSummaryModel with {} exchanges ({} chars)", history.size(), formattedHistory.length());

    try{
      String content = ChatClient.create(model)
          .prompt()
          .system(systemPrompt)
          .user(formattedHistory)
          .call()
          .content();

      if( content == null || content.isBlank() ){
        log.debug("setupSummaryModel returned empty content");
        return null;
      }

      log.debug("setupSummaryModel responded ({} chars)", content.length());
      return content;
    }catch(Exception ex){
      log.error("setupSummaryModel call failed: {}", ex.getMessage(), ex);
      throw ex;
    }
  }

  private String formatHistory(Collection<ChatEntry> history) {
    var sb = new StringBuilder();
    for( ChatEntry entry : history ){
      sb.append("GIOCATORE: ").append(entry.userMessage()).append('\n');
      sb.append("CONSIGLIERE: ").append(entry.gmResponse()).append("\n\n");
    }
    return sb.toString();
  }
}
