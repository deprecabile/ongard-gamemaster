package com.ondgard.game.chat.contract.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SseCompletedEventTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void record_exposesInconsistencyDetectedFalse() {
    var event = new SseCompletedEvent(LocalDateTime.of(2026, 3, 3, 10, 0), "The orc charges!", false);

    assertThat(event.inconsistencyDetected()).isFalse();
    assertThat(event.gmOutput()).isEqualTo("The orc charges!");
  }

  @Test
  void record_exposesInconsistencyDetectedTrue() {
    var event = new SseCompletedEvent(LocalDateTime.of(2026, 3, 3, 10, 0), "Draft with issues", true);

    assertThat(event.inconsistencyDetected()).isTrue();
    assertThat(event.gmOutput()).isEqualTo("Draft with issues");
    assertThat(event.gmOutput()).doesNotContain("[Avviso di Sistema");
  }

  @Test
  void json_containsInconsistencyDetectedField() throws Exception {
    var event = new SseCompletedEvent(LocalDateTime.of(2026, 3, 3, 10, 0), "output", true);
    String json = objectMapper.writeValueAsString(event);

    assertThat(json)
        .contains("\"inconsistencyDetected\":true")
        .contains("\"gmOutput\":\"output\"");
  }

  @Test
  void json_inconsistencyDetectedFalse_serializesCorrectly() throws Exception {
    var event = new SseCompletedEvent(LocalDateTime.of(2026, 3, 3, 10, 0), "clean output", false);
    String json = objectMapper.writeValueAsString(event);

    assertThat(json)
        .contains("\"inconsistencyDetected\":false")
        .contains("\"gmOutput\":\"clean output\"");
  }
}
