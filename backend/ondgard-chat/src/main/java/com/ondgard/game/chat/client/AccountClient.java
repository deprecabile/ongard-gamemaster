package com.ondgard.game.chat.client;

import com.ondgard.game.contract.account.AddTokensRequest;
import com.ondgard.game.contract.account.CampaignInitTokenRequest;
import com.ondgard.game.contract.account.CheckLimitResponse;
import com.ondgard.game.contract.account.UserHashRequest;
import com.ondgard.game.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class AccountClient {

  private final RestClient restClient;

  public AccountClient(@Value( "${ondgard.account.url}" ) String accountUrl) {
    this.restClient = RestClient.builder().baseUrl(accountUrl).build();
  }

  /**
   * Sincrono — chiamato alla registrazione utente (Step 7).
   */
  public void initUserLimits(String userHash) {
    log.debug("Requesting account service to init user limits for userHash={}", userHash);
    try{
      restClient.post()
          .uri("/api/internal/user/init")
          .contentType(MediaType.APPLICATION_JSON)
          .body(new UserHashRequest(userHash))
          .retrieve()
          .toBodilessEntity();
      log.debug("User limits initialized successfully for userHash={}", userHash);
    }catch(Exception e){
      log.error("Failed to init user limits on account service for userHash={}", userHash, e);
      throw new AppException("Failed to init user limits on account service");
    }
  }

  /**
   * Sincrono — chiamato alla creazione campagna (Step 7).
   */
  public void initCampaignUsage(String characterHash, String userHash) {
    log.debug("Requesting account service to init campaign usage for characterHash={}", characterHash);
    try{
      restClient.post()
          .uri("/api/internal/campaign/init")
          .contentType(MediaType.APPLICATION_JSON)
          .body(new CampaignInitTokenRequest(characterHash, userHash))
          .retrieve()
          .toBodilessEntity();
      log.debug("Campaign usage initialized successfully for characterHash={}", characterHash);
    }catch(Exception e){
      log.error("Failed to init campaign usage on account service for characterHash={}", characterHash, e);
      throw new AppException("Failed to init campaign usage on account service");
    }
  }

  /**
   * Asincrono fire-and-forget — chiamato dal decorator dopo ogni chiamata LLM (Step 8).
   */
  @Async
  public void addTokensAsync(String characterHash, long tokens) {
    try{
      restClient.post()
          .uri("/api/internal/tokens")
          .contentType(MediaType.APPLICATION_JSON)
          .body(new AddTokensRequest(characterHash, tokens))
          .retrieve()
          .toBodilessEntity();
    }catch(Exception e){
      log.error("Failed to track {} tokens for character {}", tokens, characterHash, e);
    }
  }

  /**
   * Sincrono — chiamato dall'orchestrator prima della pipeline (Step 8).
   */
  public CheckLimitResponse checkLimit(String userHash) {
    log.debug("Checking token limit for userHash={}", userHash);
    try{
      return restClient.get()
          .uri("/api/internal/check-limit?userHash={userHash}", userHash)
          .retrieve()
          .body(CheckLimitResponse.class);
    }catch(Exception e){
      log.error("Failed to check token limit on account service for userHash={}", userHash, e);
      throw new AppException("Failed to check token limit on account service");
    }
  }
}
