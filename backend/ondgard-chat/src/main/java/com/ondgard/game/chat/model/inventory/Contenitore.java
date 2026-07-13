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
public class Contenitore implements Serializable, Comparable<Contenitore> {

  @LlmRequired private String nome;
  @Builder.Default private Collection<InventoryItem> oggetti = Collections.emptyList();

  @Override
  public int compareTo(Contenitore o) {
    if( this.nome == null ){
      return (o.nome == null) ? 0 : -1;
    }
    if( o.nome == null ){
      return 1;
    }
    return this.nome.compareTo(o.nome);
  }
}
