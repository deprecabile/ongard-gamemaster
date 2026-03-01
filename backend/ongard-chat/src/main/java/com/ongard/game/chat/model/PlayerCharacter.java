package com.ongard.game.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerCharacter implements Serializable {

  private String characterHash;
  private UUID userHash;
  private GameRace race;
  private String name;
  private String description;
  private LocalDateTime created;
}
