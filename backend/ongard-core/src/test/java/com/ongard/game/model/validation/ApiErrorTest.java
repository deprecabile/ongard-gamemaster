package com.ongard.game.model.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorTest {

  @Test
  void fromMessage_withString_shouldUseGenericCode() {
    ApiError error = ApiError.fromMessage("something went wrong");

    assertThat(error.getMessages()).hasSize(1);
    Message msg = error.getMessages().iterator().next();
    assertThat(msg.getCode()).isEqualTo("GC_500_00");
    assertThat(msg.getMessage()).isEqualTo("something went wrong");
    assertThat(msg.getLevel()).isEqualTo(Message.Level.ERROR);
  }

  @Test
  void fromMessage_withCodeAndString_shouldUseProvidedCode() {
    ApiError error = ApiError.fromMessage("CUSTOM_400", "bad input");

    assertThat(error.getMessages()).hasSize(1);
    Message msg = error.getMessages().iterator().next();
    assertThat(msg.getCode()).isEqualTo("CUSTOM_400");
    assertThat(msg.getMessage()).isEqualTo("bad input");
    assertThat(msg.getLevel()).isEqualTo(Message.Level.ERROR);
  }

  @Test
  void shouldImplementMessageHolder() {
    ApiError error = ApiError.fromMessage("test");
    assertThat(error).isInstanceOf(MessageHolder.class);
  }
}
