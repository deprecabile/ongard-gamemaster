package com.ondgard.game.account.repository;

import com.ondgard.game.account.entity.CampaignTokenUsageEntity;
import com.ondgard.game.account.model.dto.CharacterTokenUsageDto;
import com.ondgard.game.account.model.dto.PlayerTokenUsageDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CampaignTokenUsageRepository extends JpaRepository<CampaignTokenUsageEntity, String> {

  @Modifying
  @Query( value = """
      UPDATE campaign_token_usage
      SET month_tokens = 0, last_reset_month = :currentMonth, updated = :now
      WHERE character_hash = :characterHash AND last_reset_month <> :currentMonth
      """, nativeQuery = true )
  int resetMonthIfNeeded(
      @Param( "characterHash" ) String characterHash,
      @Param( "currentMonth" ) String currentMonth,
      @Param( "now" ) Instant now);

  @Modifying
  @Query( value = """
      UPDATE campaign_token_usage
      SET month_tokens = 0, last_reset_month = :currentMonth, updated = :now
      WHERE user_hash = :userHash AND last_reset_month <> :currentMonth
      """, nativeQuery = true )
  int resetMonthIfNeededForUser(
      @Param( "userHash" ) String userHash,
      @Param( "currentMonth" ) String currentMonth,
      @Param( "now" ) Instant now);

  @Modifying
  @Query( value = """
      UPDATE campaign_token_usage
      SET total_tokens = total_tokens + :tokens,
          month_tokens = month_tokens + :tokens,
          updated = :now
      WHERE character_hash = :characterHash
      """, nativeQuery = true )
  int addTokens(
      @Param( "characterHash" ) String characterHash,
      @Param( "tokens" ) long tokens,
      @Param( "now" ) Instant now);

  @Query( """
      SELECT new com.ondgard.game.account.model.dto.PlayerTokenUsageDto(
          COALESCE(SUM(c.totalTokens), 0),
          COALESCE(SUM(c.monthTokens), 0)
      )
      FROM CampaignTokenUsageEntity c
      WHERE c.userHash = :userHash
      """ )
  PlayerTokenUsageDto sumTokensByUserHash(@Param( "userHash" ) String userHash);

  @Query( """
      SELECT new com.ondgard.game.account.model.dto.CharacterTokenUsageDto(
          c.characterHash, c.totalTokens, c.monthTokens
      )
      FROM CampaignTokenUsageEntity c
      WHERE c.userHash = :userHash
      """ )
  List<CharacterTokenUsageDto> findUsageByCharacterForUser(@Param( "userHash" ) String userHash);
}
