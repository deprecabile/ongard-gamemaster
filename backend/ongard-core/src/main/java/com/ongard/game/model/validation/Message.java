package com.ongard.game.model.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Message implements Serializable {

  public static enum Level {
    INFO,
    WARNING,
    ERROR
  }

  private Level level;
  private String code;
  private String message;
  private LocalDateTime timestamp;
}
