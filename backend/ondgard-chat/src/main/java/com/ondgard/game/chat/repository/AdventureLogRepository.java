package com.ondgard.game.chat.repository;

import com.ondgard.game.chat.entity.AdventureLogEntity;
import com.ondgard.game.chat.repository.projection.AdventureLogTurnProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdventureLogRepository extends JpaRepository<AdventureLogEntity, Long> {

  @Query( value = """
      SELECT u.turn_number AS "turnNumber", u.content AS "userMessage",
             g.content AS "gmResponse", u.created::timestamp AS "created"
      FROM (SELECT DISTINCT ON (turn_number) turn_number, content, created
            FROM adventure_log WHERE campaign_id = :campaignId AND role = 'USER'
            ORDER BY turn_number, id DESC) u
      JOIN (SELECT DISTINCT ON (turn_number) turn_number, content
            FROM adventure_log WHERE campaign_id = :campaignId AND role = 'GM'
            ORDER BY turn_number, id DESC) g ON u.turn_number = g.turn_number
      ORDER BY u.turn_number DESC
      LIMIT :limit
      """, nativeQuery = true )
  List<AdventureLogTurnProjection> findRecentTurns(
      @Param( "campaignId" ) Long campaignId, @Param( "limit" ) int limit);

  @Query( value = """
      SELECT u.turn_number AS "turnNumber", u.content AS "userMessage",
             g.content AS "gmResponse", u.created::timestamp AS "created"
      FROM (SELECT DISTINCT ON (turn_number) turn_number, content, created
            FROM adventure_log WHERE campaign_id = :campaignId AND role = 'USER'
            ORDER BY turn_number, id DESC) u
      JOIN (SELECT DISTINCT ON (turn_number) turn_number, content
            FROM adventure_log WHERE campaign_id = :campaignId AND role = 'GM'
            ORDER BY turn_number, id DESC) g ON u.turn_number = g.turn_number
      WHERE u.turn_number BETWEEN :startTurn AND :endTurn
      ORDER BY u.turn_number ASC
      """, nativeQuery = true )
  List<AdventureLogTurnProjection> findTurnRange(
      @Param( "campaignId" ) Long campaignId,
      @Param( "startTurn" ) int startTurn,
      @Param( "endTurn" ) int endTurn);

  @Query( value = """
      SELECT COALESCE(MAX(turn_number), 0)
      FROM adventure_log WHERE campaign_id = :campaignId
      """, nativeQuery = true )
  int findMaxTurnNumber(@Param( "campaignId" ) Long campaignId);

  @Query( value = """
      SELECT content FROM adventure_log
      WHERE campaign_id = :campaignId AND role = 'GM'
      ORDER BY turn_number ASC LIMIT 1
      """, nativeQuery = true )
  Optional<String> findFirstGmResponse(@Param( "campaignId" ) Long campaignId);
}
