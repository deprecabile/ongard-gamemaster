package com.ondgard.game.chat.service.agent;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class SummaryAgent {

  private final ChatModel model;
  private String promptTemplate;

  public SummaryAgent(@Qualifier( "summaryModel" ) ChatModel model) {
    this.model = model;
  }

  @PostConstruct
  void loadTemplate() throws IOException {
    var resource = new ClassPathResource("prompts/summary_system.md");
    promptTemplate = resource.getContentAsString(StandardCharsets.UTF_8);
    log.info("Summary prompt template loaded ({} chars)", promptTemplate.length());
  }

  public String generate(String language, String currentSummary, String bufferSnapshot) {
    log.debug("SummaryAgent: generating summary from buffer ({} chars)", bufferSnapshot.length());

    final String userMessage = buildUserMessage(bufferSnapshot, currentSummary);
    final String resolvedPrompt = promptTemplate.replace("{{language}}", language);

    final String result = ChatClient.create(model)
        .prompt()
        .system(resolvedPrompt)
        .user(userMessage)
        .call()
        .content();

    log.debug("SummaryAgent: summary generated ({} chars)", result != null ? result.length() : 0);
    return result;
  }

  private String buildUserMessage(String bufferSnapshot, String currentSummary) {
    var sb = new StringBuilder();

    if( currentSummary != null && !currentSummary.isBlank() ){
      sb.append("## Riassunto Precedente\n\n").append(currentSummary).append("\n\n");
      sb.append("## Nuovi Eventi da Integrare\n\n").append(bufferSnapshot).append("\n\n");
      sb.append("Integra il riassunto precedente con i nuovi eventi in un testo coerente e continuo. ")
          .append("Non ripetere informazioni gia' presenti nel riassunto precedente ")
          .append("a meno che i nuovi eventi non le modifichino.");
    } else{
      sb.append("## Eventi da Riassumere\n\n").append(bufferSnapshot).append("\n\n");
      sb.append("Questo e' il primo ciclo della campagna. Riassumi gli eventi forniti.");
    }

    return sb.toString();
  }
}
