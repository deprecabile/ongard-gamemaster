package com.ondgard.game.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties( prefix = "ondgard.game.summary" )
public class SummaryProperties {

  private int triggerEveryNTurns = 10;
  private int recentHistorySize = 10;
}
