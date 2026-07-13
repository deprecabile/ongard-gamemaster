package com.ondgard.game.warmup;

import com.ondgard.game.warmup.config.WarmupProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties( WarmupProperties.class )
public class OndgardWarmupApplication {

  public static void main(String[] args) {
    SpringApplication.run(OndgardWarmupApplication.class, args);
  }
}
