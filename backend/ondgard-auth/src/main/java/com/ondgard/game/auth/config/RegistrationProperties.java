package com.ondgard.game.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties( prefix = "ondgard.auth.registration" )
@Getter
@Setter
public class RegistrationProperties {

  private boolean enabled = true;
}
