package com.ondgard.game.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties( prefix = "ondgard.game.advisor" )
public class AdvisorProperties {

  private int maxHistoryExchanges = 10;
  private int maxTurnDelta = 30;
  private int maxDisplayExchanges = 50;
}
