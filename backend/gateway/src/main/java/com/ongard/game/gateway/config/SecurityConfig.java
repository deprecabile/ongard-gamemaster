package com.ongard.game.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  @Value( "${jwt.secret}" )
  private String jwtSecret;

  @Bean
  @Order( 1 )
  public SecurityWebFilterChain publicSecurityFilterChain(ServerHttpSecurity http) {
    return http
        .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/auth/**"))
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
        .build();
  }

  @Bean
  @Order( 2 )
  public SecurityWebFilterChain protectedSecurityFilterChain(ServerHttpSecurity http) {
    return http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder()))
        )
        .build();
  }

  @Bean
  public ReactiveJwtDecoder reactiveJwtDecoder() {
    byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
    SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    return NimbusReactiveJwtDecoder.withSecretKey(secretKey).build();
  }
}
