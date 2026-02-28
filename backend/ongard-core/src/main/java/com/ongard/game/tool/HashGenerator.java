package com.ongard.game.tool;

import java.util.concurrent.ThreadLocalRandom;

public final class HashGenerator {
  private static final int HASH_LENGTH = 32;
  private static final String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789àòèù";

  public static String generateHash() {
    final StringBuilder sb = new StringBuilder(HASH_LENGTH);
    for( int i = 0; i < HASH_LENGTH; i++ ){
      final int index = ThreadLocalRandom.current().nextInt(alphabet.length());
      sb.append(alphabet.charAt(index));
    }
    return sb.toString();
  }
}
