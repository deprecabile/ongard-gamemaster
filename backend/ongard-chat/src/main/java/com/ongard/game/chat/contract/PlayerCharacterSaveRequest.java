package com.ongard.game.chat.contract;

import com.ongard.game.chat.model.GameRace;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerCharacterSaveRequest implements Serializable {

  private GameRace race;
  private String name;
  private String description;
}
