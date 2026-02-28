package com.ongard.game.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashGeneratorTest {

  private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789àòèù";

  @Test
  void generateHash_shouldReturn32Characters() {
    String hash = HashGenerator.generateHash();
    assertThat(hash).hasSize(32);
  }

  @Test
  void generateHash_shouldContainOnlyAlphabetCharacters() {
    String hash = HashGenerator.generateHash();
    for( char c : hash.toCharArray() ){
      assertThat(ALPHABET).contains(String.valueOf(c));
    }
  }

  @Test
  void generateHash_shouldProduceUniqueValues() {
    String hash1 = HashGenerator.generateHash();
    String hash2 = HashGenerator.generateHash();
    assertThat(hash1).isNotEqualTo(hash2);
  }
}
