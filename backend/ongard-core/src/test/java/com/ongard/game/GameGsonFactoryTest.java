package com.ongard.game;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GameGsonFactoryTest {

  private record Payload( LocalDate date, LocalDateTime dateTime ) {
  }

  @Test
  void build_shouldReturnConfiguredGson() {
    Gson gson = GameGsonFactory.build();
    assertThat(gson).isNotNull();
  }

  // ── LocalDate ──────────────────────────────────────────────────────────

  @Test
  void localDate_serialized_shouldUseIsoFormat() {
    Gson gson = GameGsonFactory.build();
    Payload payload = new Payload(LocalDate.of(2024, 6, 15), null);
    String json = gson.toJson(payload);
    assertThat(json).contains("\"2024-06-15\"");
  }

  @Test
  void localDate_nullSerialized_shouldProduceJsonNull() {
    Gson gson = GameGsonFactory.build();
    String json = gson.toJson(null, LocalDate.class);
    assertThat(json).isEqualTo("null");
  }

  @Test
  void localDate_deserialized_shouldParseIsoFormat() {
    Gson gson = GameGsonFactory.build();
    String json = "{\"date\":\"2024-06-15\",\"dateTime\":null}";
    Payload payload = gson.fromJson(json, Payload.class);
    assertThat(payload.date()).isEqualTo(LocalDate.of(2024, 6, 15));
  }

  @Test
  void localDate_nullDeserialized_shouldReturnNull() {
    Gson gson = GameGsonFactory.build();
    String json = "{\"date\":null,\"dateTime\":null}";
    Payload payload = gson.fromJson(json, Payload.class);
    assertThat(payload.date()).isNull();
  }

  // ── LocalDateTime ──────────────────────────────────────────────────────

  @Test
  void localDateTime_serialized_shouldUseIsoFormat() {
    Gson gson = GameGsonFactory.build();
    Payload payload = new Payload(null, LocalDateTime.of(2024, 6, 15, 10, 30, 0));
    String json = gson.toJson(payload);
    assertThat(json).contains("\"2024-06-15T10:30:00\"");
  }

  @Test
  void localDateTime_nullSerialized_shouldProduceJsonNull() {
    Gson gson = GameGsonFactory.build();
    String json = gson.toJson(null, LocalDateTime.class);
    assertThat(json).isEqualTo("null");
  }

  @Test
  void localDateTime_deserialized_shouldParseIsoFormat() {
    Gson gson = GameGsonFactory.build();
    String json = "{\"date\":null,\"dateTime\":\"2024-06-15T10:30:00\"}";
    Payload payload = gson.fromJson(json, Payload.class);
    assertThat(payload.dateTime()).isEqualTo(LocalDateTime.of(2024, 6, 15, 10, 30, 0));
  }

  @Test
  void localDateTime_nullDeserialized_shouldReturnNull() {
    Gson gson = GameGsonFactory.build();
    String json = "{\"date\":null,\"dateTime\":null}";
    Payload payload = gson.fromJson(json, Payload.class);
    assertThat(payload.dateTime()).isNull();
  }

  // ── Private constructor ────────────────────────────────────────────────

  @Test
  void constructor_shouldBePrivate() throws Exception {
    Constructor<GameGsonFactory> constructor = GameGsonFactory.class.getDeclaredConstructor();
    assertThat(constructor.canAccess(null)).isFalse();
    constructor.setAccessible(true);
    Object instance = constructor.newInstance();
    assertThat(instance).isNotNull();
  }
}
