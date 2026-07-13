package com.ondgard.game.chat.config.ai;

import com.ondgard.game.chat.client.AccountClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Decoratore trasparente per ChatModel che intercetta e logga
 * gli errori HTTP (503, 429, ecc.) dalle chiamate al provider LLM,
 * e tracka il consumo token verso ondgard-account.
 */
@Slf4j
@RequiredArgsConstructor
public class TokenAwareChatModelDecorator implements ChatModel {

  private final ChatModel delegate;
  private final String modelLabel;
  private final AccountClient accountClient;

  @Override
  public ChatResponse call(Prompt prompt) {
    try{
      ChatResponse response = delegate.call(prompt);
      trackTokens(response);
      return response;
    }catch(Throwable ex){
      logError(ex);
      throw ex;
    }
  }

  @Override
  public Flux<ChatResponse> stream(Prompt prompt) {
    return delegate.stream(prompt).doOnError(this::logError);
  }

  private void trackTokens(ChatResponse response) {
    String characterHash = TokenTrackingContext.get();
    if( characterHash == null ) return;
    try{
      long totalTokens = response.getMetadata().getUsage().getTotalTokens();
      if( totalTokens > 0 ){
        accountClient.addTokensAsync(characterHash, totalTokens);
      }
    }catch(Exception ex){
      log.warn("[{}] Failed to extract/track token usage: {}", modelLabel, ex.getMessage());
    }
  }

  private void logError(Throwable ex) {
    var sb = new StringBuilder();
    sb.append("[").append(modelLabel).append("] LLM call failed");

    Throwable current = ex;
    int depth = 0;
    while( current != null && depth < 5 ){
      sb.append("\n  cause[").append(depth).append("]: ")
          .append(current.getClass().getSimpleName())
          .append(" — ").append(current.getMessage());
      current = current.getCause();
      depth++;
    }

    log.error(sb.toString());
  }
}
