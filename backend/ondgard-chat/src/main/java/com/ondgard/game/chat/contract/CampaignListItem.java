package com.ondgard.game.chat.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignListItem {

  private String characterHash;
  private String characterName;
  private String raceCode;
  private int currentTurn;
  private String currentLocation;
  private LocalDateTime lastUpdate;
  private String narrativePreview;
}
