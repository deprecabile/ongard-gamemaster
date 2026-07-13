package com.ondgard.game.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties( prefix = "ondgard.setup.session" )
public class SetupSessionProperties {

  private Duration ttl = Duration.ofMinutes(20);
}
