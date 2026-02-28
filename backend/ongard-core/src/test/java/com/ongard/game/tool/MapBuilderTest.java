package com.ongard.game.tool;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MapBuilderTest {

  @Test
  void createAddBuild_shouldProduceCorrectMap() {
    Map<String, Integer> map = MapBuilder.<String, Integer>create()
        .add("a", 1)
        .add("b", 2)
        .build();

    assertThat(map).containsEntry("a", 1).containsEntry("b", 2).hasSize(2);
  }

  @Test
  void build_shouldReturnDefensiveCopy() {
    MapBuilder<String, Integer> builder = MapBuilder.<String, Integer>create().add("a", 1);
    Map<String, Integer> map1 = builder.build();
    map1.put("b", 2);

    Map<String, Integer> map2 = builder.build();
    assertThat(map2).doesNotContainKey("b");
  }
}
