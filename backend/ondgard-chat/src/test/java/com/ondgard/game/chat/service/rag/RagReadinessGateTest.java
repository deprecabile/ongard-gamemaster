package com.ondgard.game.chat.service.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagReadinessGateTest {

  @Test
  void isReady_returnsFalse_beforeMarkReady() {
    var gate = new RagReadinessGate();
    assertThat(gate.isReady()).isFalse();
  }

  @Test
  void isReady_returnsTrue_afterMarkReady() {
    var gate = new RagReadinessGate();
    gate.markReady();
    assertThat(gate.isReady()).isTrue();
  }

  @Test
  void markReady_isIdempotent() {
    var gate = new RagReadinessGate();
    gate.markReady();
    gate.markReady();
    assertThat(gate.isReady()).isTrue();
  }
}
