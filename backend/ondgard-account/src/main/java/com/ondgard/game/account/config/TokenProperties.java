package com.ondgard.game.account.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties( prefix = "ondgard.token" )
public class TokenProperties {
  private long defaultLimitMonth = 500_000;
  private long defaultLimitTotal = 500_000;
}
