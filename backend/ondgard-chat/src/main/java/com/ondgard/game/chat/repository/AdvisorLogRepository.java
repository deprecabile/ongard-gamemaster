package com.ondgard.game.chat.repository;

import com.ondgard.game.chat.entity.AdvisorLogEntity;
import com.ondgard.game.chat.repository.projection.AdvisorLogProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdvisorLogRepository extends JpaRepository<AdvisorLogEntity, Long> {

  @Query( """
      SELECT new com.ondgard.game.chat.repository.projection.AdvisorLogProjection(
          e.turnNumber, e.userMessage, e.advisorResponse, e.created
      )
      FROM AdvisorLogEntity e
      WHERE e.campaign.characterId = :campaignId
      ORDER BY e.created DESC, e.id DESC
      """ )
  List<AdvisorLogProjection> findRecentByCampaign(
      @Param( "campaignId" ) Long campaignId, Pageable pageable);

  @Query( """
      SELECT new com.ondgard.game.chat.repository.projection.AdvisorLogProjection(
          e.turnNumber, e.userMessage, e.advisorResponse, e.created
      )
      FROM AdvisorLogEntity e
      WHERE e.campaign.characterId = :campaignId
        AND e.turnNumber >= :minTurn
      ORDER BY e.created DESC, e.id DESC
      """ )
  List<AdvisorLogProjection> findForPrompt(
      @Param( "campaignId" ) Long campaignId,
      @Param( "minTurn" ) int minTurn,
      Pageable pageable);
}
