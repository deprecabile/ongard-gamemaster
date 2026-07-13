package com.ondgard.game.account;

import com.google.gson.Gson;
import com.ondgard.game.GameGsonFactory;
import com.ondgard.game.account.contract.UpdateLimitsRequest;
import com.ondgard.game.account.repository.CampaignTokenUsageRepository;
import com.ondgard.game.account.repository.TokenUsageLimitRepository;
import com.ondgard.game.account.service.TokenUsageService;
import com.ondgard.game.header.GameUserHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TokenUsageExternalApiTest extends TestcontainersConfiguration {

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenUsageLimitRepository tokenUsageLimitRepo;
  @Autowired private CampaignTokenUsageRepository campaignTokenUsageRepo;
  @Autowired private TokenUsageService tokenUsageService;

  private final Gson gson = GameGsonFactory.build();

  private final List<String> createdUserHashes = new ArrayList<>();
  private final List<String> createdCharHashes = new ArrayList<>();

  @AfterEach
  void cleanup() {
    createdCharHashes.forEach(h -> campaignTokenUsageRepo.deleteById(h));
    createdUserHashes.forEach(h -> tokenUsageLimitRepo.deleteAll(
        tokenUsageLimitRepo.findAll().stream()
            .filter(e -> e.getUserHash().equals(h)).toList()));
    createdCharHashes.clear();
    createdUserHashes.clear();
  }

  // ======================== GET /api/token-usage ========================

  @Test
  void getUsageOverview_noData_returnsNullLimitsAndZeros() throws Exception {
    mockMvc.perform(get("/api/token-usage")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader("user-ext-empty")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limits").doesNotExist())
        .andExpect(jsonPath("$.usage.totalTokens").value(0))
        .andExpect(jsonPath("$.usage.monthTokens").value(0))
        .andExpect(jsonPath("$.characters", hasSize(0)));
  }

  @Test
  void getUsageOverview_withData_returnsLimitsAndAggregatedUsage() throws Exception {
    String userHash = "user-ext-data";
    String charHash1 = "char-ext-1";
    String charHash2 = "char-ext-2";
    createdUserHashes.add(userHash);
    createdCharHashes.add(charHash1);
    createdCharHashes.add(charHash2);

    tokenUsageService.initUserLimits(userHash);
    tokenUsageService.initCampaignUsage(charHash1, userHash);
    tokenUsageService.initCampaignUsage(charHash2, userHash);
    tokenUsageService.addTokens(charHash1, 300);
    tokenUsageService.addTokens(charHash2, 700);

    mockMvc.perform(get("/api/token-usage")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader(userHash)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limits.limitMonth").value(500_000))
        .andExpect(jsonPath("$.limits.limitTotal").value(500_000))
        .andExpect(jsonPath("$.limits.version").value(1))
        .andExpect(jsonPath("$.usage.totalTokens").value(1000))
        .andExpect(jsonPath("$.usage.monthTokens").value(1000))
        .andExpect(jsonPath("$.characters", hasSize(2)));
  }

  @Test
  void getUsageOverview_missingHeader_returns400() throws Exception {
    mockMvc.perform(get("/api/token-usage"))
        .andExpect(status().isBadRequest());
  }

  // ======================== PUT /api/token-usage/limits ========================

  @Test
  void updateLimits_createsNewVersion() throws Exception {
    String userHash = "user-ext-upd";
    createdUserHashes.add(userHash);

    tokenUsageService.initUserLimits(userHash);

    mockMvc.perform(put("/api/token-usage/limits")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader(userHash))
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new UpdateLimitsRequest(1_000_000, 5_000_000))))
        .andExpect(status().isOk());

    // Verify via GET that the new limits are active with version 2
    mockMvc.perform(get("/api/token-usage")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader(userHash)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limits.limitMonth").value(1_000_000))
        .andExpect(jsonPath("$.limits.limitTotal").value(5_000_000))
        .andExpect(jsonPath("$.limits.version").value(2));
  }

  @Test
  void updateLimits_noExistingLimits_createsVersion1() throws Exception {
    String userHash = "user-ext-new";
    createdUserHashes.add(userHash);

    mockMvc.perform(put("/api/token-usage/limits")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader(userHash))
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new UpdateLimitsRequest(100_000, 200_000))))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/token-usage")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader(userHash)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limits.limitMonth").value(100_000))
        .andExpect(jsonPath("$.limits.limitTotal").value(200_000))
        .andExpect(jsonPath("$.limits.version").value(1));
  }

  @Test
  void updateLimits_monthGreaterThanTotal_returns400() throws Exception {
    mockMvc.perform(put("/api/token-usage/limits")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader("user-ext-val"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new UpdateLimitsRequest(500_000, 100_000))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messages[0].code").value("OA_400_03"));
  }

  @Test
  void updateLimits_zeroMonth_returns400() throws Exception {
    mockMvc.perform(put("/api/token-usage/limits")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader("user-ext-val2"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new UpdateLimitsRequest(0, 100_000))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messages[0].code").value("OA_400_01"));
  }

  @Test
  void updateLimits_zeroTotal_returns400() throws Exception {
    mockMvc.perform(put("/api/token-usage/limits")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader("user-ext-val3"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new UpdateLimitsRequest(100_000, 0))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messages[0].code").value("OA_400_02"));
  }

  @Test
  void updateLimits_missingHeader_returns400() throws Exception {
    mockMvc.perform(put("/api/token-usage/limits")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new UpdateLimitsRequest(100_000, 200_000))))
        .andExpect(status().isBadRequest());
  }

  // ======================== helpers ========================

  private String buildUserHeader(String userHash) {
    GameUserHeader header = GameUserHeader.builder()
        .userId(userHash)
        .username("test-user")
        .build();
    return gson.toJson(header);
  }
}
