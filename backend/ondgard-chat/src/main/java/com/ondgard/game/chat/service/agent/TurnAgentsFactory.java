package com.ondgard.game.chat.service.agent;

import com.ondgard.game.chat.model.CampaignQuestLog;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.GameScene;
import com.ondgard.game.chat.model.agent.QuestlogUpdaterResponse;
import com.ondgard.game.chat.model.inventory.Inventory;

import java.util.Collection;
import java.util.function.Function;

public interface TurnAgentsFactory {

  LoreReviewer buildInventoryReviewer(Inventory currentInventory, Collection<ChatEntry> recentHistory);

  Function<String, Inventory> buildInventoryUpdater(Inventory currentInventory, String userAction);

  Function<String, QuestlogUpdaterResponse> buildQuestlogUpdater(CampaignQuestLog currentQuestLog, GameScene scene);

  Function<String, Inventory> buildInventoryInitializer();

  Function<String, QuestlogUpdaterResponse> buildQuestlogInitializer();

  Function<String, GameScene> buildSceneInitializer();

  Function<String, GameScene> buildSceneUpdater(GameScene currentScene);
}
