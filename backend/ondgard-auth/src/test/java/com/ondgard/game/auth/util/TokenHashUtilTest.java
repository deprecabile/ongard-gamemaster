package com.ondgard.game.auth.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHashUtilTest {

  @Test
  void sha256Hex_returns64CharLowercaseHex() {
    String hash = TokenHashUtil.sha256Hex("test-token");

    assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
  }

  @Test
  void sha256Hex_sameInputProducesSameOutput() {
    String hash1 = TokenHashUtil.sha256Hex("my-token");
    String hash2 = TokenHashUtil.sha256Hex("my-token");

    assertThat(hash1).isEqualTo(hash2);
  }

  @Test
  void sha256Hex_differentInputProducesDifferentOutput() {
    String hash1 = TokenHashUtil.sha256Hex("token-a");
    String hash2 = TokenHashUtil.sha256Hex("token-b");

    assertThat(hash1).isNotEqualTo(hash2);
  }

  @Test
  void sha256Hex_knownVector() {
    // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
    assertThat(TokenHashUtil.sha256Hex("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}
