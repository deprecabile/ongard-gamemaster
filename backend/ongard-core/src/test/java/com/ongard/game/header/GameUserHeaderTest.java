package com.ongard.game.header;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameUserHeaderTest {

  @Test
  void headerName_shouldBeCorrect() {
    assertThat(GameUserHeader.HEADER_NAME).isEqualTo("x-ongard-user");
  }

  @Test
  void builder_shouldCreateHeader() {
    GameUserHeader header = GameUserHeader.builder()
        .userId("user-123")
        .username("testUser")
        .build();

    assertThat(header.getUserId()).isEqualTo("user-123");
    assertThat(header.getUsername()).isEqualTo("testUser");
  }
}
