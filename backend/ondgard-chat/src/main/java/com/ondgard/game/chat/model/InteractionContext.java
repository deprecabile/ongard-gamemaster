package com.ondgard.game.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class InteractionContext {
  private final String userMessage;
  private final PlayerCharacter character;
  private final CampaignContext campaignContext;
  private final String outputLangIsoCode;
  private final String outputLang;
  private final String loreLangCode;
}
