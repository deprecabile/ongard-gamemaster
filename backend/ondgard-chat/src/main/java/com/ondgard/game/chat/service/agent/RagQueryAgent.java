package com.ondgard.game.chat.service.agent;

import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.GameScene;
import com.ondgard.game.chat.model.agent.RagQueryResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagQueryAgent {

  @Qualifier( "ragQueryModel" )
  private final ChatModel ragQueryModel;

  private String promptTemplate;

  @PostConstruct
  void loadTemplate() throws IOException {
    var resource = new ClassPathResource("prompts/rag_query_system.md");
    promptTemplate = resource.getContentAsString(StandardCharsets.UTF_8);
    log.info("RAG query prompt template loaded ({} chars)", promptTemplate.length());
  }

  public List<String> composeQueries(String loreLang, String userAction, GameScene scene, String questActive, ChatEntry lastTurn) {
    try{
      String userMessage = buildUserMessage(userAction, scene, questActive, lastTurn);
      String resolvedPrompt = promptTemplate.replace("{{loreLang}}", loreLang);

      var converter = new BeanOutputConverter<>(RagQueryResponse.class);

      String response = ChatClient.create(ragQueryModel)
          .prompt()
          .system(resolvedPrompt)
          .user(userMessage)
          .call()
          .content();

      RagQueryResponse parsed = converter.convert(response);
      if( parsed == null || parsed.queries() == null || parsed.queries().isEmpty() ){
        log.debug("RAG query agent returned empty queries — fallback to simple query");
        return List.of();
      }

      log.info("RAG query agent generated {} queries: {}", parsed.queries().size(), parsed.queries());
      return parsed.queries();
    }catch(Exception ex){
      log.warn("RAG query agent failed — fallback to simple query: {}", ex.getMessage());
      return List.of();
    }
  }

  // ************************************ package-private ************************************

  String buildUserMessage(String userAction, GameScene scene, String questActive, ChatEntry lastTurn) {
    var sb = new StringBuilder();

    sb.append("## Azione\n").append(userAction);

    if( scene != null ){
      sb.append("\n\n## Scena");
      if( scene.getCurrentLocation() != null ){
        sb.append("\n- Luogo: ").append(scene.getCurrentLocation());
      }
      if( scene.getGameDate() != null ){
        sb.append("\n- Data: ").append(scene.getGameDate());
      }
      if( scene.getGameTime() != null ){
        sb.append("\n- Ora: ").append(scene.getGameTime());
      }
      if( scene.getMeteo() != null ){
        sb.append("\n- Meteo: ").append(scene.getMeteo());
      }
    }

    if( questActive != null && !questActive.isBlank() ){
      sb.append("\n\n## Quest Attive\n").append(questActive);
    }

    if( lastTurn != null ){
      sb.append("\n\n## Ultimo Turno\n- Giocatore: ").append(lastTurn.userMessage())
          .append("\n- GM: ").append(lastTurn.gmResponse());
    }

    return sb.toString();
  }
}
