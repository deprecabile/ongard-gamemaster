package com.ondgard.game.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table( name = "adventure_log" )
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdventureLogEntity {

  @Id
  @GeneratedValue( strategy = GenerationType.IDENTITY )
  private Long id;

  @ManyToOne( fetch = FetchType.LAZY )
  @JoinColumn( name = "campaign_id", nullable = false )
  private CampaignEntity campaign;

  @Column( nullable = false )
  private String role;

  @Column( nullable = false )
  private String content;

  @Column( name = "turn_number", nullable = false )
  private int turnNumber;

  @CreationTimestamp
  @Column( nullable = false, updatable = false )
  private LocalDateTime created;
}
