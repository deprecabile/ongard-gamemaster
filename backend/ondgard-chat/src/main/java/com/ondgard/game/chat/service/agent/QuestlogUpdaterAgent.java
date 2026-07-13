package com.ondgard.game.chat.service.agent;

import com.ondgard.game.chat.model.CampaignQuestLog;
import com.ondgard.game.chat.model.agent.QuestlogUpdaterResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;

@Slf4j
final class QuestlogUpdaterAgent {

  private final ChatModel model;
  private final String systemPrompt;
  private final CampaignQuestLog currentQuestLog;

  QuestlogUpdaterAgent(ChatModel model, String promptTemplate, CampaignQuestLog questLog,
                       String gameDate, String gameTime) {
    this.model = model;
    this.currentQuestLog = questLog;
    this.systemPrompt = promptTemplate
        .replace("{{questActive}}", questLog.questActive() != null ? questLog.questActive() : "")
        .replace("{{questCompleted}}", questLog.questCompleted() != null ? questLog.questCompleted() : "")
        .replace("{{gameDate}}", gameDate != null ? gameDate : "sconosciuta")
        .replace("{{gameTime}}", gameTime != null ? gameTime : "sconosciuta");
  }

  public QuestlogUpdaterResponse update(String approvedDraft) {
    var converter = new BeanOutputConverter<>(QuestlogUpdaterResponse.class);

    try{
      log.debug("QuestlogUpdater: updating questlog from draft ({} chars)", approvedDraft.length());

      String response = ChatClient.create(model)
          .prompt()
          .system(systemPrompt)
          .user(approvedDraft)
          .call()
          .content();

      QuestlogUpdaterResponse result = converter.convert(response);
      log.debug("QuestlogUpdater: questlog updated successfully");
      return result;
    }catch(Exception ex){
      log.warn("QuestlogUpdater: parsing/call failed — returning current questlog. Error: {}", ex.getMessage());
      return new QuestlogUpdaterResponse(
          currentQuestLog.questActive() != null ? currentQuestLog.questActive() : "",
          currentQuestLog.questCompleted() != null ? currentQuestLog.questCompleted() : "");
    }
  }
}
