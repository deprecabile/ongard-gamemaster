package com.ondgard.game.chat.model.inventory;

import com.ondgard.game.postprocessing.LlmRequired;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtraProperty implements Serializable {

  @LlmRequired private String chiave;
  @LlmRequired private String valore;
}
