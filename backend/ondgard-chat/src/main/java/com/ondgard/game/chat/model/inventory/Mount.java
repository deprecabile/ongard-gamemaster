package com.ondgard.game.chat.model.inventory;

import com.ondgard.game.postprocessing.LlmRequired;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Mount implements Serializable {

  @LlmRequired private String nome;
  private String tipo;
  private String stato;
  @Builder.Default private Collection<ExtraProperty> extra = Collections.emptyList();
}
