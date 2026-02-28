package com.ongard.game.model.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiError implements MessageHolder {
  @Builder.Default private final Collection<Message> messages = new ArrayList<>();

  public static ApiError fromMessage(String msg) {
    return ApiError.builder()
        .messages(List.of(Message.builder()
            .level(Message.Level.ERROR)
            .code("GC_500_00")
            .message(msg)
            .build()))
        .build();
  }

  public static ApiError fromMessage(String code, String msg) {
    return ApiError.builder()
        .messages(List.of(Message.builder()
            .level(Message.Level.ERROR)
            .code(code)
            .message(msg)
            .build()))
        .build();
  }
}
