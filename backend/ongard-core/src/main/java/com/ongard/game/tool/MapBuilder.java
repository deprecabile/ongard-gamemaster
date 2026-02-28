package com.ongard.game.tool;

import java.util.HashMap;
import java.util.Map;

public final class MapBuilder<K, V> {

  private final Map<K, V> map;

  private MapBuilder() {
    map = new HashMap<>();
  }

  public static <K, V> MapBuilder<K, V> create() {
    return new MapBuilder<>();
  }

  public MapBuilder<K, V> add(K key, V value) {
    map.put(key, value);
    return this;
  }

  public Map<K, V> build() {
    return new HashMap<>(map);
  }

}
