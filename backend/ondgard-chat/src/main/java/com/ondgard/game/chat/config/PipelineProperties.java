package com.ondgard.game.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties( prefix = "ondgard.game.pipeline" )
public class PipelineProperties {

  private int maxAttempts = 3;
  private Duration sseTimeout = Duration.ofMinutes(3);
  private int fullLoreTurns = 12;
}
