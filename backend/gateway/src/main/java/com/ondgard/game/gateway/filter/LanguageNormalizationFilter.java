package com.ondgard.game.gateway.filter;

import com.ondgard.game.gateway.util.LanguageUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LanguageNormalizationFilter implements GlobalFilter, Ordered {

  private static final String ACCEPT_LANGUAGE = "Accept-Language";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    final String raw = exchange.getRequest().getHeaders().getFirst(ACCEPT_LANGUAGE);
    final String normalized = LanguageUtils.resolveLanguageCode(raw);
    final ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
        .headers(h -> h.set(ACCEPT_LANGUAGE, normalized))
        .build();
    return chain.filter(exchange.mutate().request(mutatedRequest).build());
  }

  @Override
  public int getOrder() {
    return -10;
  }
}
