package com.ondgard.game.chat.model.inventory;

import com.ondgard.game.collection.NotNullTreeSet;
import com.ondgard.game.postprocessing.LlmRequired;
import com.ondgard.game.postprocessing.OndgardLlmSchema;
import com.ondgard.game.tool.ComparatorBuilder;
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
@OndgardLlmSchema( "INVENTORY" )
public class Inventory implements Serializable {

  @LlmRequired private int money;
  @Builder.Default private Collection<Mount> mount = Collections.emptyList();
  @LlmRequired @Builder.Default private Collection<Contenitore> contenitori = Collections.emptyList();

  public static Inventory empty() {
    return Inventory.builder()
        .money(0)
        .mount(Collections.emptyList())
        .contenitori(NotNullTreeSet.builder(ComparatorBuilder.buildComparator(String::compareTo, Contenitore::getNome))
            .of(
                Contenitore.builder().nome("equipaggiato").build(),
                Contenitore.builder().nome("backpack").build()
            )
            .build())
        .build();
  }
}
