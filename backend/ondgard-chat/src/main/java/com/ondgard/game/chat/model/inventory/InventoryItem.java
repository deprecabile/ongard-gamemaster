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
public class InventoryItem implements Serializable, Comparable<InventoryItem> {

  @LlmRequired private String nome;
  @LlmRequired private String descrizione;
  @LlmRequired private int quantita;
  @LlmRequired private double peso;
  @Builder.Default private Collection<ExtraProperty> extra = Collections.emptyList();

  @Override
  public int compareTo(InventoryItem o) {
    if( this.nome == null ){
      return (o.nome == null) ? 0 : -1;
    }
    if( o.nome == null ){
      return 1;
    }
    return this.nome.compareTo(o.nome);
  }
}
