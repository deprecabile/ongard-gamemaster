package com.ondgard.game.chat.model.setup;

import com.ondgard.game.chat.model.ChatEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignSetupSession implements Serializable {

  private String userHash;

  @Builder.Default private Collection<ChatEntry> history = new ArrayList<>();

  private String raceCode;
  private String characterName;
  private String characterPrompt;
  private String startingSituation;
  private String archetypeCode;
}
