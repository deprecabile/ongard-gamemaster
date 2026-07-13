package com.ondgard.game.chat.service.agent;

import com.ondgard.game.chat.model.agent.ReviewerResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;

@Slf4j
final class BaseLoreReviewer implements LoreReviewer {

  private final ChatModel validatorModel;
  private final String category;
  private final String systemPrompt;

  BaseLoreReviewer(ChatModel validatorModel, String promptTemplate, String category, String loreContent) {
    this.validatorModel = validatorModel;
    this.category = category;
    this.systemPrompt = promptTemplate
        .replace("{{topic}}", category)
        .replace("{{loreContent}}", loreContent);
  }

  @Override
  public ReviewerResponse review(String draft) {
    var converter = new BeanOutputConverter<>(ReviewerResponse.class);

    try{
      log.debug("Reviewer [{}]: reviewing draft ({} chars)", category, draft.length());

      String response = ChatClient.create(validatorModel)
          .prompt()
          .system(systemPrompt)
          .user(draft)
          .call()
          .content();

      ReviewerResponse result = converter.convert(response);
      if( !result.isPass() ){
        log.debug("Reviewer [{}]: isPass=false, because: {}", category, result.feedbackReason());
      }
      return result;
    }catch(Exception ex){
      log.warn("Reviewer [{}]: parsing/call failed — fail-open. Error: {}", category, ex.getMessage());
      return new ReviewerResponse(true, category, "Parsing error — fail-open");
    }
  }

  @Override
  public String topic() {
    return category;
  }
}
