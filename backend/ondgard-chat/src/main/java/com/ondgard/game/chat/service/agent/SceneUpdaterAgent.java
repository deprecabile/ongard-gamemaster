package com.ondgard.game.chat.service.agent;

import com.ondgard.game.chat.model.GameScene;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;

@Slf4j
final class SceneUpdaterAgent {

  private final ChatModel model;
  private final String systemPrompt;
  private final GameScene currentScene;

  SceneUpdaterAgent(ChatModel model, String promptTemplate, GameScene currentScene) {
    this.model = model;
    this.currentScene = currentScene;
    this.systemPrompt = promptTemplate
        .replace("{{currentLocation}}", currentScene.getCurrentLocation() != null ? currentScene.getCurrentLocation() : "Sconosciuto")
        .replace("{{gameDate}}", currentScene.getGameDate() != null ? currentScene.getGameDate() : "sconosciuta")
        .replace("{{gameTime}}", currentScene.getGameTime() != null ? currentScene.getGameTime() : "sconosciuta")
        .replace("{{meteo}}", currentScene.getMeteo() != null ? currentScene.getMeteo() : "sconosciuto")
        .replace("{{temperature}}", currentScene.getTemperature() != null ? currentScene.getTemperature() : "sconosciuta");
  }

  public GameScene update(String approvedDraft) {
    var converter = new BeanOutputConverter<>(GameScene.class);

    try{
      log.debug("SceneUpdater: updating scene from draft ({} chars)", approvedDraft.length());

      String response = ChatClient.create(model)
          .prompt()
          .system(systemPrompt)
          .user(approvedDraft)
          .call()
          .content();

      GameScene updated = converter.convert(response);
      log.debug("SceneUpdater: scene updated successfully");
      return updated;
    }catch(Exception ex){
      log.warn("SceneUpdater: parsing/call failed — returning current scene. Error: {}", ex.getMessage());
      return currentScene;
    }
  }
}
