package com.ongard.game.header;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameUserHeader implements Serializable {
  public static final String HEADER_NAME = "x-ongard-user";
  private String userId;
  private String username;
}
