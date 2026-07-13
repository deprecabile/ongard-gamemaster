package com.ondgard.game.chat.config.ai;

import com.ondgard.game.chat.client.AccountClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith( MockitoExtension.class )
class TokenAwareChatModelDecoratorTest {

  @Mock private ChatModel delegate;
  @Mock private AccountClient accountClient;

  private TokenAwareChatModelDecorator decorator;

  @BeforeEach
  void setUp() {
    decorator = new TokenAwareChatModelDecorator(delegate, "testModel", accountClient);
  }

  @AfterEach
  void cleanup() {
    TokenTrackingContext.clear();
  }

  private ChatResponse responseWithTokens(int prompt, int completion) {
    var usage = new DefaultUsage(prompt, completion);
    var metadata = ChatResponseMetadata.builder().usage(usage).build();
    return new ChatResponse(List.of(), metadata);
  }

  @Test
  void call_withContext_tracksTokens() {
    TokenTrackingContext.set("char-hash-1");
    var response = responseWithTokens(100, 50);
    when(delegate.call(any(Prompt.class))).thenReturn(response);

    ChatResponse result = decorator.call(new Prompt("test"));

    assertThat(result).isSameAs(response);
    verify(accountClient).addTokensAsync("char-hash-1", 150L);
  }

  @Test
  void call_withoutContext_skipsTracking() {
    // No TokenTrackingContext set
    var response = responseWithTokens(100, 50);
    when(delegate.call(any(Prompt.class))).thenReturn(response);

    ChatResponse result = decorator.call(new Prompt("test"));

    assertThat(result).isSameAs(response);
    verifyNoInteractions(accountClient);
  }

  @Test
  void call_zeroTokens_skipsTracking() {
    TokenTrackingContext.set("char-hash-1");
    var response = responseWithTokens(0, 0);
    when(delegate.call(any(Prompt.class))).thenReturn(response);

    decorator.call(new Prompt("test"));

    verifyNoInteractions(accountClient);
  }

  @Test
  void call_delegateThrows_rethrowsWithoutTracking() {
    TokenTrackingContext.set("char-hash-1");
    when(delegate.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM down"));

    assertThatThrownBy(() -> decorator.call(new Prompt("test")))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("LLM down");

    verifyNoInteractions(accountClient);
  }

  @Test
  void call_nullUsageMetadata_doesNotThrow() {
    TokenTrackingContext.set("char-hash-1");
    // ChatResponse with no metadata/usage — getMetadata() returns default with null usage
    var response = new ChatResponse(List.of());
    when(delegate.call(any(Prompt.class))).thenReturn(response);

    ChatResponse result = decorator.call(new Prompt("test"));

    assertThat(result).isSameAs(response);
    verifyNoInteractions(accountClient);
  }

  @Test
  void call_tracksTokensFromCorrectThread_viaWrap() throws Exception {
    TokenTrackingContext.set("threaded-char");
    var response = responseWithTokens(200, 100);
    when(delegate.call(any(Prompt.class))).thenReturn(response);

    var resultRef = new AtomicReference<ChatResponse>();
    CompletableFuture.supplyAsync(TokenTrackingContext.wrap(() -> {
      resultRef.set(decorator.call(new Prompt("test")));
      return null;
    })).join();

    assertThat(resultRef.get()).isSameAs(response);
    verify(accountClient).addTokensAsync("threaded-char", 300L);
  }

  @Test
  void call_withoutWrap_inOtherThread_skipsTracking() throws Exception {
    TokenTrackingContext.set("main-char");
    var response = responseWithTokens(200, 100);
    when(delegate.call(any(Prompt.class))).thenReturn(response);

    // Call without wrap — ThreadLocal is NOT propagated
    CompletableFuture.supplyAsync(() -> {
      return decorator.call(new Prompt("test"));
    }).join();

    verifyNoInteractions(accountClient);
  }

  @Test
  void call_accountClientThrows_doesNotPropagate() {
    TokenTrackingContext.set("char-err");
    var response = responseWithTokens(50, 50);
    when(delegate.call(any(Prompt.class))).thenReturn(response);
    doThrow(new RuntimeException("network error")).when(accountClient).addTokensAsync(anyString(), anyLong());

    // addTokensAsync is @Async fire-and-forget, but even if it throws synchronously
    // in test (no Spring proxy), the decorator catches it in trackTokens
    // Actually addTokensAsync is called directly here (no Spring AOP), so if it throws
    // the decorator's trackTokens catches it. Let's verify no propagation.
    ChatResponse result = decorator.call(new Prompt("test"));

    assertThat(result).isSameAs(response);
  }
}
