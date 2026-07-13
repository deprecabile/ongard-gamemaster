package com.ondgard.game.header;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameUserHeaderTest {

  @Test
  void headerName_shouldBeCorrect() {
    assertThat(GameUserHeader.HEADER_NAME).isEqualTo("x-ondgard-user");
  }

  @Test
  void builder_shouldCreateHeader() {
    GameUserHeader header = GameUserHeader.builder().userId("user-123").username("testUser").build();

    assertThat(header.getUserId()).isEqualTo("user-123");
    assertThat(header.getUsername()).isEqualTo("testUser");
  }

  @Test
  void builder_shouldDefaultLanguageToEn() {
    GameUserHeader header = GameUserHeader.builder()
        .userId("id").username("user").build();

    assertThat(header.getLanguage()).isEqualTo("en");
  }

  @Test
  void noArgsConstructor_shouldDefaultLanguageToEn() {
    GameUserHeader header = new GameUserHeader();

    assertThat(header.getLanguage()).isEqualTo("en");
  }
}
