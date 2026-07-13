package com.ondgard.game.chat.service.agent;

import com.ondgard.game.chat.model.GameScene;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;

@Slf4j
final class SceneInitializerAgent {

  private static final GameScene DEFAULT_SCENE = GameScene.builder()
      .currentLocation("Sconosciuto")
      .gameDate("1 Gennaio 1000")
      .gameTime("08:00")
      .meteo("Sereno")
      .temperature("12°C")
      .build();

  private final ChatModel model;
  private final String systemPrompt;

  SceneInitializerAgent(ChatModel model, String promptTemplate) {
    this.model = model;
    this.systemPrompt = promptTemplate;
  }

  public GameScene initialize(String initialContext) {
    var converter = new BeanOutputConverter<>(GameScene.class);

    try{
      log.debug("SceneInitializer: initializing scene from context ({} chars)", initialContext.length());

      String response = ChatClient.create(model)
          .prompt()
          .system(systemPrompt)
          .user(initialContext)
          .call()
          .content();

      GameScene scene = converter.convert(response);
      log.debug("SceneInitializer: scene initialized successfully");
      return scene;
    }catch(Exception ex){
      log.warn("SceneInitializer: parsing/call failed — returning default scene. Error: {}", ex.getMessage());
      return DEFAULT_SCENE;
    }
  }
}
