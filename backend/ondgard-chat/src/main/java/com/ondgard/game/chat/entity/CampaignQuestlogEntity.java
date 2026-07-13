package com.ondgard.game.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table( name = "campaign_questlog" )
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignQuestlogEntity {

  @Id
  @GeneratedValue( strategy = GenerationType.IDENTITY )
  private Long id;

  @ManyToOne( fetch = FetchType.LAZY )
  @JoinColumn( name = "campaign_id", nullable = false )
  private CampaignEntity campaign;

  @Column( name = "turn_number", nullable = false )
  private int turnNumber;

  @Column( name = "quest_active" )
  private String questActive;

  @Column( name = "quest_completed" )
  private String questCompleted;

  @CreationTimestamp
  @Column( nullable = false, updatable = false )
  private LocalDateTime created;
}
