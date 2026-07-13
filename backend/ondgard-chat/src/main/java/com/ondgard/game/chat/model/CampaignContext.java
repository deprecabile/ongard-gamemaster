package com.ondgard.game.chat.model;

import com.ondgard.game.chat.model.inventory.Inventory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignContext implements Serializable {

  private Long campaignId;

  private List<ChatEntry> recentHistory;

  private String narrativeSummary;
  private int summaryVersion;
  private int turnsSinceLastSummary;
  private List<ChatEntry> rawSummaryBuffer;

  private int currentTurn;
  private int turnsSinceLastFlush;

  private Inventory inventory;
  private CampaignQuestLog questLog;
  private GameScene scene;

  @Builder.Default private List<String> advisorFacts = new ArrayList<>();

  private LocalDateTime lastUpdate;
}
