package com.ondgard.game.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties( prefix = "ondgard.session" )
public class SessionProperties {

  private int flushEveryNTurns = 5;
  private Duration inactivityTimeout = Duration.ofMinutes(30);
}
