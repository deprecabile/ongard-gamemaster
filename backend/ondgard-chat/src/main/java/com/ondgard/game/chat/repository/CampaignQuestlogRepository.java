package com.ondgard.game.chat.repository;

import com.ondgard.game.chat.entity.CampaignQuestlogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CampaignQuestlogRepository extends JpaRepository<CampaignQuestlogEntity, Long> {

  @Query( value = """
      SELECT * FROM campaign_questlog
      WHERE campaign_id = :campaignId
      ORDER BY turn_number DESC
      LIMIT 1
      """, nativeQuery = true )
  Optional<CampaignQuestlogEntity> findLatestByCampaignId(@Param( "campaignId" ) Long campaignId);
}
