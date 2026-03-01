package com.ongard.game.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collection;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RaceAttributes implements Serializable {

  private float minHeight;
  private float maxHeight;
  private Collection<String> favoriteBiomes;
}
