package com.ongard.game.model.validation;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTest {

  @Test
  void builder_shouldCreateMessage() {
    LocalDateTime now = LocalDateTime.now();
    Message message = Message.builder()
        .level(Message.Level.ERROR)
        .code("TEST_001")
        .message("test message")
        .timestamp(now)
        .build();

    assertThat(message.getLevel()).isEqualTo(Message.Level.ERROR);
    assertThat(message.getCode()).isEqualTo("TEST_001");
    assertThat(message.getMessage()).isEqualTo("test message");
    assertThat(message.getTimestamp()).isEqualTo(now);
  }

  @Test
  void levelEnum_shouldContainAllValues() {
    assertThat(Message.Level.values()).containsExactly(
        Message.Level.INFO,
        Message.Level.WARNING,
        Message.Level.ERROR
    );
  }
}
