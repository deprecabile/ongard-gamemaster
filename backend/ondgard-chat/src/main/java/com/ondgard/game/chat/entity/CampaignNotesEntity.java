package com.ondgard.game.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table( name = "campaign_notes" )
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignNotesEntity {

  @Id
  @Column( name = "campaign_id" )
  private Long campaignId;

  @OneToOne( fetch = FetchType.LAZY )
  @MapsId
  @JoinColumn( name = "campaign_id" )
  private CampaignEntity campaign;

  @Builder.Default
  @Column( nullable = false )
  private String content = "";

  @UpdateTimestamp
  @Column( nullable = false )
  private LocalDateTime updated;
}
