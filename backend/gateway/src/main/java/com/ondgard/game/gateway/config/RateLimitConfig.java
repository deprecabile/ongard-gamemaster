package com.ondgard.game.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

  @Bean
  public KeyResolver remoteAddrKeyResolver() {
    return exchange -> {
      String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
      if( forwarded != null && !forwarded.isBlank() ){
        return Mono.just(forwarded.split(",")[0].trim());
      }
      var remoteAddress = exchange.getRequest().getRemoteAddress();
      String ip = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
      return Mono.just(ip);
    };
  }
}
