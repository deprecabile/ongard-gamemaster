package com.ongard.game.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BadRequestExceptionTest {

  @Test
  void constructor_withString_shouldCreateApiError() {
    BadRequestException ex = new BadRequestException("invalid input");

    assertThat(ex.getMessage()).isEqualTo("invalid input");
    assertThat(ex.getApiError()).isNotNull();
    assertThat(ex.getApiError().getMessages()).hasSize(1);
    assertThat(ex.getApiError().getMessages().iterator().next().getCode()).isEqualTo("GC_500_00");
  }

  @Test
  void constructor_withCodeAndMessage_shouldUseProvidedCode() {
    BadRequestException ex = new BadRequestException("AU_400_01", "username taken");

    assertThat(ex.getMessage()).isEqualTo("username taken");
    assertThat(ex.getApiError().getMessages()).hasSize(1);
    assertThat(ex.getApiError().getMessages().iterator().next().getCode()).isEqualTo("AU_400_01");
  }
}
