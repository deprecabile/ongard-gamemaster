package com.ondgard.game.chat.service.agent;

import com.ondgard.game.chat.service.LoreProvider;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoreReviewerFactory {

  @Qualifier( "validatorModel" )
  private final ChatModel validatorModel;
  private final LoreProvider loreProvider;

  private String reviewerPromptTemplate;
  @Getter private String feedbackTemplate;

  @PostConstruct
  void loadTemplates() throws IOException {
    var reviewerResource = new ClassPathResource("prompts/reviewer_system.md");
    reviewerPromptTemplate = reviewerResource.getContentAsString(StandardCharsets.UTF_8);
    log.info("Reviewer prompt template loaded ({} chars)", reviewerPromptTemplate.length());

    var feedbackResource = new ClassPathResource("prompts/gm_feedback.md");
    feedbackTemplate = feedbackResource.getContentAsString(StandardCharsets.UTF_8);
    log.info("GM feedback template loaded ({} chars)", feedbackTemplate.length());
  }

  public LoreReviewer build(String loreLangCode, String category) {
    log.debug("Building reviewer for category: {} (lang={})", category, loreLangCode);
    return new BaseLoreReviewer(validatorModel, reviewerPromptTemplate, category, loreProvider.get(loreLangCode).getLore(category));
  }

}
