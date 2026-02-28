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
public class BffClient {

  private final RestClient restClient;

  public BffClient(@Value( "${bff.url}" ) String bffUrl) {
    this.restClient = RestClient.builder()
        .baseUrl(bffUrl)
        .build();
  }

  public void createUser(UUID userHash, String username) {
    log.debug("Requesting BFF to create game user for userHash={}", userHash);
    try{
      restClient.post()
          .uri("/api/user")
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("userHash", userHash, "username", username))
          .retrieve()
          .toBodilessEntity();
      log.debug("Game user created successfully for userHash={}", userHash);
    }catch(Exception e){
      log.error("Failed to create game user on BFF for userHash={}", userHash, e);
      throw new AppException("Failed to sync user with game service");
    }
  }
}
