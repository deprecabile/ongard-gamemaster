package com.ongard.game.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameRace implements Serializable {

  private String code;
  private String name;
  private String description;
  private RaceAttributes attributes;
}
