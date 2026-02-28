package com.ongard.game.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoResultExceptionTest {

  @Test
  void constructor_shouldCreateExceptionWithNoMessage() {
    NoResultException ex = new NoResultException();

    assertThat(ex).isInstanceOf(RuntimeException.class);
    assertThat(ex.getMessage()).isNull();
  }
}
