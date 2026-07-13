package com.ondgard.game.chat.contract;

import com.ondgard.game.chat.model.GameRace;
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
