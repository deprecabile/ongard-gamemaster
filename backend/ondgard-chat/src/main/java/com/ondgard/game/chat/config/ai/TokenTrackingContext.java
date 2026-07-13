package com.ondgard.game.chat.config.ai;

import java.util.function.Supplier;

public final class TokenTrackingContext {

  private static final ThreadLocal<String> CHARACTER_HASH = new ThreadLocal<>();

  private TokenTrackingContext() {
  }

  public static void set(String characterHash) {
    CHARACTER_HASH.set(characterHash);
  }

  public static String get() {
    return CHARACTER_HASH.get();
  }

  public static void clear() {
    CHARACTER_HASH.remove();
  }

  public static Runnable wrap(Runnable task) {
    String captured = get();
    return () -> {
      set(captured);
      try{
        task.run();
      }finally{
        clear();
      }
    };
  }

  public static <T> Supplier<T> wrap(Supplier<T> task) {
    String captured = get();
    return () -> {
      set(captured);
      try{
        return task.get();
      }finally{
        clear();
      }
    };
  }
}
