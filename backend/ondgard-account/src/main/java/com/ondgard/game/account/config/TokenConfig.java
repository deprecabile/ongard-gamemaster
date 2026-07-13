package com.ondgard.game.account.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties( TokenProperties.class )
public class TokenConfig {
}
