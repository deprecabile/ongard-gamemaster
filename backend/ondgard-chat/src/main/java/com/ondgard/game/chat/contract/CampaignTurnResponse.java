package com.ondgard.game.chat.contract;

import com.ondgard.game.chat.model.CampaignQuestLog;
import com.ondgard.game.chat.model.GameScene;
import com.ondgard.game.chat.model.inventory.Inventory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignTurnResponse {

  private String characterHash;
  private int currentTurn;
  private Inventory inventory;
  private CampaignQuestLog questLog;
  private GameScene scene;
  private LocalDateTime lastUpdate;
}
