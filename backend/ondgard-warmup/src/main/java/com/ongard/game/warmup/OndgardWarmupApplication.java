package com.ongard.game.warmup;

import com.ongard.game.warmup.config.WarmupProperties;
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
