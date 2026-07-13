package com.ondgard.game.chat.repository;

import com.ondgard.game.chat.entity.PlayerInventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlayerInventoryRepository extends JpaRepository<PlayerInventoryEntity, Long> {

  @Query( value = """
      SELECT * FROM player_inventory
      WHERE campaign_id = :campaignId
      ORDER BY version DESC
      LIMIT 1
      """, nativeQuery = true )
  Optional<PlayerInventoryEntity> findLatestByCampaignId(@Param( "campaignId" ) Long campaignId);
}
