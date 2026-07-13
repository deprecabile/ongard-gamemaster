package com.ondgard.game.account;

import com.google.gson.Gson;
import com.ondgard.game.GameGsonFactory;
import com.ondgard.game.account.repository.CampaignTokenUsageRepository;
import com.ondgard.game.account.repository.TokenUsageLimitRepository;
import com.ondgard.game.account.service.TokenUsageService;
import com.ondgard.game.contract.account.AddTokensRequest;
import com.ondgard.game.contract.account.CampaignInitTokenRequest;
import com.ondgard.game.contract.account.UserHashRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TokenUsageIntegrationTest extends TestcontainersConfiguration {

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenUsageLimitRepository tokenUsageLimitRepo;
  @Autowired private CampaignTokenUsageRepository campaignTokenUsageRepo;
  @Autowired private TokenUsageService tokenUsageService;
  @Autowired private EntityManager em;

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

  // ======================== initUserLimits ========================

  @Test
  void initUserLimits_returns201_andCreatesLimitWithDefaults() throws Exception {
    String userHash = "user-init-1";
    createdUserHashes.add(userHash);

    mockMvc.perform(post("/api/internal/user/init")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new UserHashRequest(userHash))))
        .andExpect(status().isCreated());

    var found = tokenUsageLimitRepo.findTopByUserHashOrderByVersionDesc(userHash);
    assertThat(found).isPresent();
    assertThat(found.get().getLimitMonth()).isEqualTo(500_000);
    assertThat(found.get().getLimitTotal()).isEqualTo(500_000);
    assertThat(found.get().getVersion()).isEqualTo(1);
  }

  @Test
  void initUserLimits_duplicate_returns409() throws Exception {
    String userHash = "user-dup-1";
    createdUserHashes.add(userHash);

    mockMvc.perform(post("/api/internal/user/init")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new UserHashRequest(userHash))))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/internal/user/init")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new UserHashRequest(userHash))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.messages[0].code").value("OA_409_00"));
  }

  // ======================== initCampaignUsage ========================

  @Test
  void initCampaignUsage_returns201_andCreatesUsageRow() throws Exception {
    String charHash = "char-init-1";
    String userHash = "user-cmp-1";
    createdCharHashes.add(charHash);
    createdUserHashes.add(userHash);

    mockMvc.perform(post("/api/internal/campaign/init")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new CampaignInitTokenRequest(charHash, userHash))))
        .andExpect(status().isCreated());

    var found = campaignTokenUsageRepo.findById(charHash);
    assertThat(found).isPresent();
    assertThat(found.get().getTotalTokens()).isZero();
    assertThat(found.get().getMonthTokens()).isZero();
    assertThat(found.get().getLastResetMonth()).isEqualTo(currentMonth());
  }

  @Test
  void initCampaignUsage_duplicate_returns409() throws Exception {
    String charHash = "char-dup-1";
    String userHash = "user-cmp-dup";
    createdCharHashes.add(charHash);
    createdUserHashes.add(userHash);

    mockMvc.perform(post("/api/internal/campaign/init")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new CampaignInitTokenRequest(charHash, userHash))))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/internal/campaign/init")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new CampaignInitTokenRequest(charHash, userHash))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.messages[0].code").value("OA_409_00"));
  }

  // ======================== addTokens ========================

  @Test
  void addTokens_returns200_andIncrementsCounters() throws Exception {
    String charHash = "char-add-1";
    String userHash = "user-add-1";
    createdCharHashes.add(charHash);
    createdUserHashes.add(userHash);

    mockMvc.perform(post("/api/internal/campaign/init")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new CampaignInitTokenRequest(charHash, userHash))))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/internal/tokens")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new AddTokensRequest(charHash, 500))))
        .andExpect(status().isOk());

    var found = campaignTokenUsageRepo.findById(charHash);
    assertThat(found).isPresent();
    assertThat(found.get().getTotalTokens()).isEqualTo(500);
    assertThat(found.get().getMonthTokens()).isEqualTo(500);
  }

  @Test
  void addTokens_nonExistentCharacter_returns200_noEffect() throws Exception {
    mockMvc.perform(post("/api/internal/tokens")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new AddTokensRequest("char-ghost", 500))))
        .andExpect(status().isOk());
  }

  // ======================== checkLimit ========================

  @Test
  void checkLimit_noLimitConfigured_returnsExceededTrue() throws Exception {
    mockMvc.perform(get("/api/internal/check-limit")
            .param("userHash", "unknown-user"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exceeded").value(true))
        .andExpect(jsonPath("$.type").value("NO_LIMIT_CONFIGURED"));
  }

  @Test
  void checkLimit_underLimit_returnsExceededFalse() throws Exception {
    String userHash = "user-under-1";
    String charHash = "char-under-1";
    createdUserHashes.add(userHash);
    createdCharHashes.add(charHash);

    mockMvc.perform(post("/api/internal/user/init")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new UserHashRequest(userHash))))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/internal/campaign/init")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new CampaignInitTokenRequest(charHash, userHash))))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/internal/tokens")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new AddTokensRequest(charHash, 100))))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/internal/check-limit")
            .param("userHash", userHash))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exceeded").value(false))
        .andExpect(jsonPath("$.type").doesNotExist());
  }

  @Test
  void checkLimit_overMonthlyLimit_returnsMonthly() throws Exception {
    String userHash = "user-over-m";
    String charHash = "char-over-m";
    createdUserHashes.add(userHash);
    createdCharHashes.add(charHash);

    // Init user with small limits
    tokenUsageService.initUserLimits(userHash);
    tokenUsageService.updateLimits(userHash, 100, 999_999);

    mockMvc.perform(post("/api/internal/campaign/init")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new CampaignInitTokenRequest(charHash, userHash))))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/internal/tokens")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new AddTokensRequest(charHash, 200))))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/internal/check-limit")
            .param("userHash", userHash))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exceeded").value(true))
        .andExpect(jsonPath("$.type").value("MONTHLY"));
  }

  @Test
  void checkLimit_overTotalLimit_returnsTotal() throws Exception {
    String userHash = "user-over-t";
    String charHash = "char-over-t";
    createdUserHashes.add(userHash);
    createdCharHashes.add(charHash);

    // Init user with small total limit
    tokenUsageService.initUserLimits(userHash);
    tokenUsageService.updateLimits(userHash, 999_999, 100);

    mockMvc.perform(post("/api/internal/campaign/init")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new CampaignInitTokenRequest(charHash, userHash))))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/internal/tokens")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new AddTokensRequest(charHash, 200))))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/internal/check-limit")
            .param("userHash", userHash))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exceeded").value(true))
        .andExpect(jsonPath("$.type").value("TOTAL"));
  }

  @Test
  void checkLimit_limitZero_returnsBlocked() throws Exception {
    String userHash = "user-zero";
    createdUserHashes.add(userHash);

    tokenUsageService.initUserLimits(userHash);
    tokenUsageService.updateLimits(userHash, 0, 0);

    mockMvc.perform(get("/api/internal/check-limit")
            .param("userHash", userHash))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exceeded").value(true))
        .andExpect(jsonPath("$.type").value("TOTAL"));
  }

  // ======================== getUsageOverview ========================

  @Test
  void getUsageOverview_noData_returnsNullLimitAndZeros() {
    var overview = tokenUsageService.getUsageOverview("nonexistent-user");

    assertThat(overview.limit()).isNull();
    assertThat(overview.usage().totalTokens()).isZero();
    assertThat(overview.usage().monthTokens()).isZero();
    assertThat(overview.characters()).isEmpty();
  }

  @Test
  void getUsageOverview_returnsLimitAndAggregatedUsage() {
    String userHash = "user-overview";
    String charHash1 = "char-ov-1";
    String charHash2 = "char-ov-2";
    createdUserHashes.add(userHash);
    createdCharHashes.add(charHash1);
    createdCharHashes.add(charHash2);

    tokenUsageService.initUserLimits(userHash);
    tokenUsageService.initCampaignUsage(charHash1, userHash);
    tokenUsageService.initCampaignUsage(charHash2, userHash);
    tokenUsageService.addTokens(charHash1, 300);
    tokenUsageService.addTokens(charHash2, 700);

    var overview = tokenUsageService.getUsageOverview(userHash);

    assertThat(overview.limit()).isNotNull();
    assertThat(overview.limit().getLimitMonth()).isEqualTo(500_000);
    assertThat(overview.limit().getLimitTotal()).isEqualTo(500_000);
    assertThat(overview.usage().totalTokens()).isEqualTo(1000);
    assertThat(overview.usage().monthTokens()).isEqualTo(1000);
    assertThat(overview.characters()).hasSize(2);
    assertThat(overview.characters()).anySatisfy(c -> {
      assertThat(c.characterHash()).isEqualTo(charHash1);
      assertThat(c.totalTokens()).isEqualTo(300);
    });
    assertThat(overview.characters()).anySatisfy(c -> {
      assertThat(c.characterHash()).isEqualTo(charHash2);
      assertThat(c.totalTokens()).isEqualTo(700);
    });
  }

  // ======================== helpers ========================

  private String currentMonth() {
    return YearMonth.now(ZoneOffset.UTC).toString();
  }
}
