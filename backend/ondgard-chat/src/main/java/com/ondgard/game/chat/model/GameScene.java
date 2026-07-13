package com.ondgard.game.chat.model;

import com.ondgard.game.postprocessing.LlmRequired;
import com.ondgard.game.postprocessing.OndgardLlmSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@OndgardLlmSchema( "SCENE" )
public class GameScene implements Serializable {

  @LlmRequired private String currentLocation;
  @LlmRequired private String gameDate;
  @LlmRequired private String gameTime;
  @LlmRequired private String meteo;
  @LlmRequired private String temperature;
}
