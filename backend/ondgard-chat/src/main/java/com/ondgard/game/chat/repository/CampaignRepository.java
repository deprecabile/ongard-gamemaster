package com.ondgard.game.chat.repository;

import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.repository.projection.CampaignListProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<CampaignEntity, Long> {

  @Query( """
      SELECT c FROM CampaignEntity c
      JOIN c.character pc
      JOIN pc.user u
      WHERE pc.characterHash = :characterHash
        AND u.userHash = :userHash
      """ )
  Optional<CampaignEntity> findByCharacterHashAndUserHash(@Param( "userHash" ) UUID userHash, @Param( "characterHash" ) String characterHash);

  @Query( """
      SELECT new com.ondgard.game.chat.repository.projection.CampaignListProjection(
          c.characterId, pc.characterHash, pc.name, r.code,
          c.turnCount, c.currentLocation, c.updated,
          c.narrativeSummary, pc.description
      )
      FROM CampaignEntity c
      JOIN c.character pc
      JOIN pc.user u
      JOIN pc.race r
      WHERE u.userHash = :userHash AND c.turnCount > 0
      ORDER BY c.updated DESC
      """ )
  List<CampaignListProjection> findAllByUserHash(@Param( "userHash" ) UUID userHash);

  @Query( """
      SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
      FROM CampaignEntity c
      JOIN c.character pc
      JOIN pc.user u
      WHERE u.userHash = :userHash AND c.turnCount > 0
      """ )
  boolean existsByUserHash(@Param( "userHash" ) UUID userHash);

  @Modifying
  @Query( value = """
      INSERT INTO campaign (character_id, summary_version, turn_count, last_summary_at_turn, created, updated)
      SELECT pc.id, 0, 0, 0, NOW(), NOW()
      FROM player_character pc
      JOIN game_user gu ON gu.id = pc.user_id
      WHERE pc.character_hash = :characterHash
        AND gu.user_hash = :userHash
      """, nativeQuery = true )
  void insertCampaign(@Param( "userHash" ) UUID userHash, @Param( "characterHash" ) String characterHash);
}
