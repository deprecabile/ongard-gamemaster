package com.ondgard.game.chat.repository;

import com.ondgard.game.chat.entity.CampaignNotesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CampaignNotesRepository extends JpaRepository<CampaignNotesEntity, Long> {

  @Modifying
  @Query( value = """
      INSERT INTO campaign_notes (campaign_id, content, updated)
      VALUES (:campaignId, '', NOW())
      """, nativeQuery = true )
  void insertEmpty(@Param( "campaignId" ) Long campaignId);

  @Modifying
  @Query( "UPDATE CampaignNotesEntity n SET n.content = :content, n.updated = CURRENT_TIMESTAMP WHERE n.campaignId = :campaignId" )
  void updateContent(@Param( "campaignId" ) Long campaignId, @Param( "content" ) String content);

  Optional<CampaignNotesEntity> findByCampaignId(Long campaignId);
}
