package com.ondgard.game.chat.service.agent.advisor;

import com.ondgard.game.chat.repository.projection.AdvisorLogProjection;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdvisorAgent {

  @Qualifier( "advisorModel" )
  private final ChatModel advisorModel;

  private String instructions;

  @PostConstruct
  void loadSystemPrompt() throws IOException {
    var resource = new ClassPathResource("prompts/advisor_system_prompt.md");
    instructions = resource.getContentAsString(StandardCharsets.UTF_8);
    log.info("Advisor instructions loaded ({} chars)", instructions.length());
  }

  public String answer(String outputLang, String loreLang,
                       Collection<AdvisorLogProjection> history, String userQuestion,
                       AdvisorTools advisorTools) {

    String systemPrompt = instructions
        .replace("{{language}}", outputLang)
        .replace("{{loreLang}}", loreLang);

    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(systemPrompt));

    for( AdvisorLogProjection entry : history ){
      messages.add(new UserMessage(entry.userMessage()));
      messages.add(new AssistantMessage(entry.advisorResponse()));
    }
    messages.add(new UserMessage(userQuestion));

    log.debug("Calling advisorModel with {} history exchanges, question ({} chars)",
        history.size(), userQuestion.length());
    try{
      String content = ChatClient.create(advisorModel)
          .prompt(new Prompt(messages))
          .tools(advisorTools)
          .call()
          .content();
      log.debug("advisorModel responded ({} chars)", content != null ? content.length() : 0);
      return content;
    }catch(Exception ex){
      log.error("advisorModel call failed: {}", ex.getMessage(), ex);
      throw ex;
    }
  }
}
