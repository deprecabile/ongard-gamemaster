package com.ondgard.game.chat.service.agent;

import com.ondgard.game.chat.model.CampaignQuestLog;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.GameScene;
import com.ondgard.game.chat.model.agent.QuestlogUpdaterResponse;
import com.ondgard.game.chat.model.inventory.Inventory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Collection;
import java.util.function.Function;

class LocalizedFactory implements TurnAgentsFactory {

  private final ChatModel inventoryModel;
  private final ChatModel inventoryReviewerModel;
  private final ChatModel questModel;
  private final ChatModel sceneModel;
  private final String inventoryReviewerPrompt;
  private final String inventoryUpdaterPrompt;
  private final String inventoryInitializerPrompt;
  private final String questLogUpdaterPrompt;
  private final String questLogInitializerPrompt;
  private final String sceneUpdaterPrompt;
  private final String sceneInitializerPrompt;

  @AllArgsConstructor
  @Builder
  public static final class LocalizedFactoryParams {
    private final ChatModel inventoryModel;
    private final ChatModel inventoryReviewerModel;
    private final ChatModel questModel;
    private final ChatModel sceneModel;
    private final String inventoryReviewerPrompt;
    private final String inventoryUpdaterPrompt;
    private final String inventoryInitializerPrompt;
    private final String questLogUpdaterPrompt;
    private final String questLogInitializerPrompt;
    private final String sceneUpdaterPrompt;
    private final String sceneInitializerPrompt;
  }

  LocalizedFactory(LocalizedFactoryParams p) {
    this.inventoryModel = p.inventoryModel;
    this.inventoryReviewerModel = p.inventoryReviewerModel;
    this.questModel = p.questModel;
    this.sceneModel = p.sceneModel;
    this.inventoryReviewerPrompt = p.inventoryReviewerPrompt;
    this.inventoryUpdaterPrompt = p.inventoryUpdaterPrompt;
    this.inventoryInitializerPrompt = p.inventoryInitializerPrompt;
    this.questLogUpdaterPrompt = p.questLogUpdaterPrompt;
    this.questLogInitializerPrompt = p.questLogInitializerPrompt;
    this.sceneUpdaterPrompt = p.sceneUpdaterPrompt;
    this.sceneInitializerPrompt = p.sceneInitializerPrompt;
  }

  @Override
  public LoreReviewer buildInventoryReviewer(Inventory currentInventory, Collection<ChatEntry> recentHistory) {
    return new InventoryReviewerAgent(inventoryReviewerModel, inventoryReviewerPrompt, currentInventory, recentHistory);
  }

  @Override
  public Function<String, Inventory> buildInventoryUpdater(Inventory currentInventory, String userAction) {
    var agent = new InventoryUpdaterAgent(inventoryModel, inventoryUpdaterPrompt, currentInventory, userAction);
    return agent::update;
  }

  @Override
  public Function<String, QuestlogUpdaterResponse> buildQuestlogUpdater(CampaignQuestLog currentQuestLog, GameScene scene) {
    var agent = new QuestlogUpdaterAgent(questModel, questLogUpdaterPrompt, currentQuestLog,
        scene != null ? scene.getGameDate() : null,
        scene != null ? scene.getGameTime() : null);
    return agent::update;
  }

  @Override
  public Function<String, Inventory> buildInventoryInitializer() {
    var agent = new InventoryUpdaterAgent(inventoryModel, inventoryInitializerPrompt, Inventory.empty(), null);
    return agent::update;
  }

  @Override
  public Function<String, QuestlogUpdaterResponse> buildQuestlogInitializer() {
    CampaignQuestLog emptyLog = new CampaignQuestLog(0, "", "");
    var agent = new QuestlogUpdaterAgent(questModel, questLogInitializerPrompt, emptyLog, null, null);
    return agent::update;
  }

  @Override
  public Function<String, GameScene> buildSceneInitializer() {
    var agent = new SceneInitializerAgent(sceneModel, sceneInitializerPrompt);
    return agent::initialize;
  }

  @Override
  public Function<String, GameScene> buildSceneUpdater(GameScene currentScene) {
    var agent = new SceneUpdaterAgent(sceneModel, sceneUpdaterPrompt, currentScene);
    return agent::update;
  }
}
