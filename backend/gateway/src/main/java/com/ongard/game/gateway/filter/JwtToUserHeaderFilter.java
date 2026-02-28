package com.ongard.game.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ongard.game.header.GameUserHeader;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtToUserHeaderFilter implements GlobalFilter, Ordered {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return exchange.getPrincipal()
        .filter(JwtAuthenticationToken.class::isInstance)
        .cast(JwtAuthenticationToken.class)
        .map(JwtAuthenticationToken::getToken)
        .flatMap(jwt -> {
          ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
              .header(GameUserHeader.HEADER_NAME, buildUserHeaderValue(jwt))
              .headers(headers -> headers.remove("Authorization"))
              .build();
          return chain.filter(exchange.mutate().request(mutatedRequest).build());
        })
        .switchIfEmpty(chain.filter(exchange));
  }

  private String buildUserHeaderValue(Jwt jwt) {
    GameUserHeader header = GameUserHeader.builder()
        .userId(jwt.getSubject())
        .username(jwt.getClaimAsString("username"))
        .build();
    try{
      return objectMapper.writeValueAsString(header);
    }catch(JsonProcessingException e){
      throw new RuntimeException("Failed to serialize GameUserHeader", e);
    }
  }

  @Override
  public int getOrder() {
    return -1;
  }
}
