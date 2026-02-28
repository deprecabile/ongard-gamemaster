package com.ongard.game.authentication.client;

import com.ongard.game.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class ChatClient {

  private final RestClient restClient;

  public ChatClient(@Value("${ongard.chat.url}") String chatUrl) {
    this.restClient = RestClient.builder()
        .baseUrl(chatUrl)
        .build();
  }

  public void createUser(UUID userHash, String username) {
    log.debug("Requesting chat service to create game user for userHash={}", userHash);
    try {
      restClient.post()
          .uri("/api/user")
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("userHash", userHash, "username", username))
          .retrieve()
          .toBodilessEntity();
      log.debug("Game user created successfully for userHash={}", userHash);
    } catch (Exception e) {
      log.error("Failed to create game user on chat service for userHash={}", userHash, e);
      throw new AppException("Failed to sync user with game service");
    }
  }
}
