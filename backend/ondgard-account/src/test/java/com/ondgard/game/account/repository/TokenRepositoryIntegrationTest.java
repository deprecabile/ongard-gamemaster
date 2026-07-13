package com.ondgard.game.account.repository;

import com.ondgard.game.account.TestcontainersConfiguration;
import com.ondgard.game.account.entity.CampaignTokenUsageEntity;
import com.ondgard.game.account.entity.TokenUsageLimitEntity;
import com.ondgard.game.account.model.dto.CharacterTokenUsageDto;
import com.ondgard.game.account.model.dto.PlayerTokenUsageDto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TokenRepositoryIntegrationTest extends TestcontainersConfiguration {

  @Autowired private TokenUsageLimitRepository tokenUsageLimitRepository;
  @Autowired private CampaignTokenUsageRepository campaignTokenUsageRepository;
  @Autowired private EntityManager em;

  private static final String USER_HASH = "user-abc-123";

  // ======================== TokenUsageLimitRepository ========================

  @Test
  void findTopByUserHashOrderByVersionDesc_returnsHighestVersion() {
    tokenUsageLimitRepository.save(TokenUsageLimitEntity.builder()
        .userHash(USER_HASH)
        .limitMonth(100_000)
        .limitTotal(500_000)
        .version(1)
        .build());
    tokenUsageLimitRepository.save(TokenUsageLimitEntity.builder()
        .userHash(USER_HASH)
        .limitMonth(200_000)
        .limitTotal(1_000_000)
        .version(2)
        .build());
    em.flush();
    em.clear();

    Optional<TokenUsageLimitEntity> found = tokenUsageLimitRepository
        .findTopByUserHashOrderByVersionDesc(USER_HASH);

    assertThat(found).isPresent();
    assertThat(found.get().getVersion()).isEqualTo(2);
    assertThat(found.get().getLimitMonth()).isEqualTo(200_000);
    assertThat(found.get().getLimitTotal()).isEqualTo(1_000_000);
  }

  @Test
  void findTopByUserHashOrderByVersionDesc_noData_returnsEmpty() {
    Optional<TokenUsageLimitEntity> found = tokenUsageLimitRepository
        .findTopByUserHashOrderByVersionDesc("nonexistent");

    assertThat(found).isEmpty();
  }

  // ======================== CampaignTokenUsageRepository ========================

  @Test
  void addTokens_incrementsAtomically() {
    campaignTokenUsageRepository.save(CampaignTokenUsageEntity.builder()
        .characterHash("char-001")
        .userHash(USER_HASH)
        .totalTokens(100)
        .monthTokens(50)
        .lastResetMonth("2026-03")
        .build());
    em.flush();
    em.clear();

    Instant now = Instant.now();
    int updated = campaignTokenUsageRepository.addTokens("char-001", 250, now);
    assertThat(updated).isEqualTo(1);

    em.flush();
    em.clear();

    CampaignTokenUsageEntity reloaded = campaignTokenUsageRepository.findById("char-001").orElseThrow();
    assertThat(reloaded.getTotalTokens()).isEqualTo(350);
    assertThat(reloaded.getMonthTokens()).isEqualTo(300);
  }

  @Test
  void sumTokensByUserHash_aggregatesAcrossCampaigns() {
    campaignTokenUsageRepository.save(CampaignTokenUsageEntity.builder()
        .characterHash("char-a")
        .userHash(USER_HASH)
        .totalTokens(1000)
        .monthTokens(400)
        .lastResetMonth("2026-03")
        .build());
    campaignTokenUsageRepository.save(CampaignTokenUsageEntity.builder()
        .characterHash("char-b")
        .userHash(USER_HASH)
        .totalTokens(2000)
        .monthTokens(600)
        .lastResetMonth("2026-03")
        .build());
    em.flush();
    em.clear();

    PlayerTokenUsageDto dto = campaignTokenUsageRepository.sumTokensByUserHash(USER_HASH);

    assertThat(dto.totalTokens()).isEqualTo(3000);
    assertThat(dto.monthTokens()).isEqualTo(1000);
  }

  @Test
  void sumTokensByUserHash_noData_returnsZeros() {
    PlayerTokenUsageDto dto = campaignTokenUsageRepository.sumTokensByUserHash("nonexistent");

    assertThat(dto.totalTokens()).isZero();
    assertThat(dto.monthTokens()).isZero();
  }

  @Test
  void resetMonthIfNeeded_resetsWhenMonthChanged() {
    campaignTokenUsageRepository.save(CampaignTokenUsageEntity.builder()
        .characterHash("char-reset")
        .userHash(USER_HASH)
        .totalTokens(5000)
        .monthTokens(3000)
        .lastResetMonth("2026-02")
        .build());
    em.flush();
    em.clear();

    Instant now = Instant.now();
    int updated = campaignTokenUsageRepository.resetMonthIfNeeded("char-reset", "2026-03", now);
    assertThat(updated).isEqualTo(1);

    em.flush();
    em.clear();

    CampaignTokenUsageEntity reloaded = campaignTokenUsageRepository.findById("char-reset").orElseThrow();
    assertThat(reloaded.getMonthTokens()).isZero();
    assertThat(reloaded.getTotalTokens()).isEqualTo(5000);
    assertThat(reloaded.getLastResetMonth()).isEqualTo("2026-03");
  }

  @Test
  void resetMonthIfNeeded_noOpWhenSameMonth() {
    campaignTokenUsageRepository.save(CampaignTokenUsageEntity.builder()
        .characterHash("char-noop")
        .userHash(USER_HASH)
        .totalTokens(1000)
        .monthTokens(500)
        .lastResetMonth("2026-03")
        .build());
    em.flush();
    em.clear();

    int updated = campaignTokenUsageRepository.resetMonthIfNeeded("char-noop", "2026-03", Instant.now());
    assertThat(updated).isZero();

    em.flush();
    em.clear();

    CampaignTokenUsageEntity reloaded = campaignTokenUsageRepository.findById("char-noop").orElseThrow();
    assertThat(reloaded.getMonthTokens()).isEqualTo(500);
  }

  @Test
  void resetMonthIfNeededForUser_resetsBulk() {
    campaignTokenUsageRepository.save(CampaignTokenUsageEntity.builder()
        .characterHash("char-bulk-1")
        .userHash(USER_HASH)
        .totalTokens(1000)
        .monthTokens(400)
        .lastResetMonth("2026-02")
        .build());
    campaignTokenUsageRepository.save(CampaignTokenUsageEntity.builder()
        .characterHash("char-bulk-2")
        .userHash(USER_HASH)
        .totalTokens(2000)
        .monthTokens(600)
        .lastResetMonth("2026-02")
        .build());
    em.flush();
    em.clear();

    int updated = campaignTokenUsageRepository.resetMonthIfNeededForUser(USER_HASH, "2026-03", Instant.now());
    assertThat(updated).isEqualTo(2);

    em.flush();
    em.clear();

    PlayerTokenUsageDto dto = campaignTokenUsageRepository.sumTokensByUserHash(USER_HASH);
    assertThat(dto.monthTokens()).isZero();
    assertThat(dto.totalTokens()).isEqualTo(3000);
  }

  @Test
  void findUsageByCharacterForUser_returnsAllCampaigns() {
    campaignTokenUsageRepository.save(CampaignTokenUsageEntity.builder()
        .characterHash("char-list-1")
        .userHash(USER_HASH)
        .totalTokens(1500)
        .monthTokens(300)
        .lastResetMonth("2026-03")
        .build());
    campaignTokenUsageRepository.save(CampaignTokenUsageEntity.builder()
        .characterHash("char-list-2")
        .userHash(USER_HASH)
        .totalTokens(2500)
        .monthTokens(700)
        .lastResetMonth("2026-03")
        .build());
    em.flush();
    em.clear();

    List<CharacterTokenUsageDto> results = campaignTokenUsageRepository
        .findUsageByCharacterForUser(USER_HASH);

    assertThat(results).hasSize(2);
    assertThat(results).anySatisfy(dto -> {
      assertThat(dto.characterHash()).isEqualTo("char-list-1");
      assertThat(dto.totalTokens()).isEqualTo(1500);
      assertThat(dto.monthTokens()).isEqualTo(300);
    });
    assertThat(results).anySatisfy(dto -> {
      assertThat(dto.characterHash()).isEqualTo("char-list-2");
      assertThat(dto.totalTokens()).isEqualTo(2500);
      assertThat(dto.monthTokens()).isEqualTo(700);
    });
  }
}
