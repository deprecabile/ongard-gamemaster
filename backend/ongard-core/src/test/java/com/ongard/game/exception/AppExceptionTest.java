package com.ongard.game.exception;

import com.ongard.game.model.validation.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppExceptionTest {

  @Test
  void constructor_withString_shouldCreateApiError() {
    AppException ex = new AppException("error occurred");

    assertThat(ex.getMessage()).isEqualTo("error occurred");
    assertThat(ex.getApiError()).isNotNull();
    assertThat(ex.getApiError().getMessages()).hasSize(1);
  }

  @Test
  void constructor_withMessages_shouldWrapMessages() {
    List<Message> messages = List.of(
        Message.builder().level(Message.Level.ERROR).code("E1").message("first").build(),
        Message.builder().level(Message.Level.WARNING).code("W1").message("second").build()
    );

    AppException ex = new AppException(messages);

    assertThat(ex.getMessage()).isEqualTo("first");
    assertThat(ex.getApiError().getMessages()).hasSize(2);
  }
}
