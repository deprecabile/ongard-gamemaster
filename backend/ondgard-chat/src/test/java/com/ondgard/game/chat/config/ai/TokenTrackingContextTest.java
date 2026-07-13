package com.ondgard.game.chat.config.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TokenTrackingContextTest {

  @AfterEach
  void cleanup() {
    TokenTrackingContext.clear();
  }

  @Test
  void setAndGet_returnsValue() {
    TokenTrackingContext.set("char-123");
    assertThat(TokenTrackingContext.get()).isEqualTo("char-123");
  }

  @Test
  void get_withoutSet_returnsNull() {
    assertThat(TokenTrackingContext.get()).isNull();
  }

  @Test
  void clear_removesValue() {
    TokenTrackingContext.set("char-123");
    TokenTrackingContext.clear();
    assertThat(TokenTrackingContext.get()).isNull();
  }

  @Test
  void threadLocal_isIsolatedBetweenThreads() throws Exception {
    TokenTrackingContext.set("main-thread-hash");
    var captured = new AtomicReference<String>();

    Thread other = new Thread(() -> captured.set(TokenTrackingContext.get()));
    other.start();
    other.join();

    assertThat(captured.get()).isNull();
    assertThat(TokenTrackingContext.get()).isEqualTo("main-thread-hash");
  }

  @Test
  void wrapRunnable_propagatesToNewThread() throws Exception {
    TokenTrackingContext.set("char-abc");
    var captured = new AtomicReference<String>();

    Runnable wrapped = TokenTrackingContext.wrap(() -> captured.set(TokenTrackingContext.get()));

    CompletableFuture.runAsync(wrapped).join();

    assertThat(captured.get()).isEqualTo("char-abc");
  }

  @Test
  void wrapRunnable_clearsAfterExecution() throws Exception {
    TokenTrackingContext.set("char-abc");
    var afterExecution = new AtomicReference<String>("NOT_CLEARED");

    Runnable wrapped = TokenTrackingContext.wrap(() -> {
      // value is set during execution
    });

    CompletableFuture.runAsync(() -> {
      wrapped.run();
      afterExecution.set(TokenTrackingContext.get());
    }).join();

    assertThat(afterExecution.get()).isNull();
  }

  @Test
  void wrapRunnable_clearsOnException() throws Exception {
    TokenTrackingContext.set("char-abc");
    var afterException = new AtomicReference<String>("NOT_CLEARED");

    Runnable wrapped = TokenTrackingContext.wrap((Runnable) () -> {
      throw new RuntimeException("boom");
    });

    CompletableFuture.runAsync(() -> {
      try{
        wrapped.run();
      }catch(RuntimeException ignored){
      }
      afterException.set(TokenTrackingContext.get());
    }).join();

    assertThat(afterException.get()).isNull();
  }

  @Test
  void wrapSupplier_propagatesToNewThread() throws Exception {
    TokenTrackingContext.set("char-xyz");

    Supplier<String> wrapped = TokenTrackingContext.wrap(TokenTrackingContext::get);

    String result = CompletableFuture.supplyAsync(wrapped).join();

    assertThat(result).isEqualTo("char-xyz");
  }

  @Test
  void wrapSupplier_clearsAfterExecution() throws Exception {
    TokenTrackingContext.set("char-xyz");
    var afterExecution = new AtomicReference<String>("NOT_CLEARED");

    Supplier<String> wrapped = TokenTrackingContext.wrap(() -> "result");

    CompletableFuture.supplyAsync(() -> {
      String val = wrapped.get();
      afterExecution.set(TokenTrackingContext.get());
      return val;
    }).join();

    assertThat(afterExecution.get()).isNull();
  }

  @Test
  void wrapRunnable_capturesValueAtWrapTime_notAtRunTime() throws Exception {
    TokenTrackingContext.set("original");
    var captured = new AtomicReference<String>();
    Runnable wrapped = TokenTrackingContext.wrap(() -> captured.set(TokenTrackingContext.get()));

    // Change value AFTER wrapping — should not affect the wrapped task
    TokenTrackingContext.set("modified-after-wrap");

    CompletableFuture.runAsync(wrapped).join();

    assertThat(captured.get()).isEqualTo("original");
  }

  @Test
  void wrapRunnable_nullContext_propagatesNull() throws Exception {
    // No set — context is null
    var captured = new AtomicReference<String>("NOT_NULL");

    Runnable wrapped = TokenTrackingContext.wrap(() -> captured.set(TokenTrackingContext.get()));
    CompletableFuture.runAsync(wrapped).join();

    assertThat(captured.get()).isNull();
  }

  @Test
  void wrapSupplier_worksWithCompletableFutureChain() throws Exception {
    TokenTrackingContext.set("chain-hash");

    String result = CompletableFuture
        .supplyAsync(TokenTrackingContext.wrap(() -> TokenTrackingContext.get()))
        .thenApply(hash -> hash + "-suffix")
        .join();

    assertThat(result).isEqualTo("chain-hash-suffix");
  }

  @Test
  void parallelFutures_eachGetsCorrectContext() throws Exception {
    TokenTrackingContext.set("parallel-hash");

    var latch = new CountDownLatch(1);
    var results = new AtomicReference[3];
    for( int i = 0; i < 3; i++ ){
      results[i] = new AtomicReference<String>();
    }

    @SuppressWarnings( "unchecked" )
    AtomicReference<String>[] typedResults = results;

    CompletableFuture<?>[] futures = new CompletableFuture[3];
    for( int i = 0; i < 3; i++ ){
      final int idx = i;
      futures[idx] = CompletableFuture.supplyAsync(TokenTrackingContext.wrap(() -> {
        typedResults[idx].set(TokenTrackingContext.get());
        return null;
      }));
    }
    CompletableFuture.allOf(futures).join();

    for( int i = 0; i < 3; i++ ){
      assertThat(typedResults[i].get()).isEqualTo("parallel-hash");
    }
  }
}
