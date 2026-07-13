package com.ondgard.game.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondgard.game.header.GameUserHeader;
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
    return exchange.getPrincipal().filter(JwtAuthenticationToken.class::isInstance).cast(JwtAuthenticationToken.class).map(JwtAuthenticationToken::getToken).flatMap(jwt -> {
      ServerHttpRequest mutatedRequest = exchange.getRequest().mutate().header(GameUserHeader.HEADER_NAME, buildUserHeaderValue(jwt, exchange.getRequest())).headers(headers -> headers.remove("Authorization")).build();
      return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }).switchIfEmpty(chain.filter(exchange));
  }

  private String buildUserHeaderValue(Jwt jwt, ServerHttpRequest request) {
    String lang = request.getHeaders().getFirst("Accept-Language");
    GameUserHeader header = GameUserHeader.builder()
        .userId(jwt.getSubject())
        .username(jwt.getClaimAsString("username"))
        .language(lang != null ? lang : "en")
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
