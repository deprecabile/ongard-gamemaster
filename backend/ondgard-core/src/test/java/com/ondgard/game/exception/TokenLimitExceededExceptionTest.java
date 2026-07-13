package com.ondgard.game.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenLimitExceededExceptionTest {

  @Test
  void constructor_setsAllFields() {
    var ex = new TokenLimitExceededException("OC_429_01", "Token limit exceeded: MONTHLY", "MONTHLY");

    assertThat(ex.getMessage()).isEqualTo("Token limit exceeded: MONTHLY");
    assertThat(ex.getLimitType()).isEqualTo("MONTHLY");
    assertThat(ex.getApiError()).isNotNull();
    assertThat(ex.getApiError().getMessages()).hasSize(1);
    assertThat(ex.getApiError().getMessages().iterator().next().getCode()).isEqualTo("OC_429_01");
    assertThat(ex.getApiError().getMessages().iterator().next().getMessage()).isEqualTo("Token limit exceeded: MONTHLY");
  }
}
