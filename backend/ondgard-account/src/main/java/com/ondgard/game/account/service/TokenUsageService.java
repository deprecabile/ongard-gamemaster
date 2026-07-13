package com.ondgard.game.account.service;

import com.ondgard.game.account.config.TokenProperties;
import com.ondgard.game.account.entity.CampaignTokenUsageEntity;
import com.ondgard.game.account.entity.TokenUsageLimitEntity;
import com.ondgard.game.account.model.TokenLimitExceeded;
import com.ondgard.game.account.model.dto.TokenUsageOverviewDto;
import com.ondgard.game.account.repository.CampaignTokenUsageRepository;
import com.ondgard.game.account.repository.TokenUsageLimitRepository;
import com.ondgard.game.exception.BadRequestException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenUsageService {

  private final TokenUsageLimitRepository limitRepo;
  private final CampaignTokenUsageRepository usageRepo;
  private final TokenProperties tokenProperties;
  private final EntityManager entityManager;

  @Transactional
  public void initUserLimits(String userHash) {
    limitRepo.save(TokenUsageLimitEntity.builder()
        .userHash(userHash)
        .limitMonth(tokenProperties.getDefaultLimitMonth())
        .limitTotal(tokenProperties.getDefaultLimitTotal())
        .version(1)
        .build());
  }

  @Transactional
  public void initCampaignUsage(String characterHash, String userHash) {
    entityManager.persist(CampaignTokenUsageEntity.builder()
        .characterHash(characterHash)
        .userHash(userHash)
        .totalTokens(0)
        .monthTokens(0)
        .lastResetMonth(currentMonth())
        .build());
  }

  @Transactional
  public void addTokens(String characterHash, long tokens) {
    Instant now = Instant.now();
    usageRepo.resetMonthIfNeeded(characterHash, currentMonth(), now);
    int updated = usageRepo.addTokens(characterHash, tokens, now);
    if( updated == 0 ){
      log.warn("addTokens: character {} not found, {} tokens lost", characterHash, tokens);
    }
  }

  @Transactional
  public Optional<TokenLimitExceeded> checkLimit(String userHash) {
    var limitOpt = limitRepo.findTopByUserHashOrderByVersionDesc(userHash);
    if( limitOpt.isEmpty() ){
      return Optional.of(TokenLimitExceeded.NO_LIMIT_CONFIGURED);
    }
    var limit = limitOpt.get();

    Instant now = Instant.now();
    usageRepo.resetMonthIfNeededForUser(userHash, currentMonth(), now);

    var usage = usageRepo.sumTokensByUserHash(userHash);

    if( usage.totalTokens() >= limit.getLimitTotal() ){
      return Optional.of(TokenLimitExceeded.TOTAL);
    }
    if( usage.monthTokens() >= limit.getLimitMonth() ){
      return Optional.of(TokenLimitExceeded.MONTHLY);
    }
    return Optional.empty();
  }

  @Transactional
  public TokenUsageOverviewDto getUsageOverview(String userHash) {
    Instant now = Instant.now();
    usageRepo.resetMonthIfNeededForUser(userHash, currentMonth(), now);

    var limit = limitRepo.findTopByUserHashOrderByVersionDesc(userHash).orElse(null);
    var usage = usageRepo.sumTokensByUserHash(userHash);
    var characters = usageRepo.findUsageByCharacterForUser(userHash);

    return new TokenUsageOverviewDto(limit, usage, characters);
  }

  @Transactional
  public void updateLimitsExternal(String userHash, long limitMonth, long limitTotal) {
    if( limitMonth <= 0 ){
      throw new BadRequestException("OA_400_01", "Il limite mensile deve essere maggiore di 0");
    }
    if( limitTotal <= 0 ){
      throw new BadRequestException("OA_400_02", "Il limite totale deve essere maggiore di 0");
    }
    if( limitMonth > limitTotal ){
      throw new BadRequestException("OA_400_03", "Il limite mensile non puo' essere superiore al limite totale");
    }
    updateLimits(userHash, limitMonth, limitTotal);
  }

  @Transactional
  public void updateLimits(String userHash, long limitMonth, long limitTotal) {
    int nextVersion = limitRepo.findTopByUserHashOrderByVersionDesc(userHash)
        .map(current -> current.getVersion() + 1)
        .orElse(1);

    limitRepo.save(TokenUsageLimitEntity.builder()
        .userHash(userHash)
        .limitMonth(limitMonth)
        .limitTotal(limitTotal)
        .version(nextVersion)
        .build());
  }

  private String currentMonth() {
    return YearMonth.now(ZoneOffset.UTC).toString();
  }
}
