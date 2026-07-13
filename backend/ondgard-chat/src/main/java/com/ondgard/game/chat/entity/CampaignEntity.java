package com.ondgard.game.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table( name = "campaign" )
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignEntity {

  @Id
  @Column( name = "character_id" )
  private Long characterId;

  @OneToOne( fetch = FetchType.LAZY )
  @MapsId
  @JoinColumn( name = "character_id" )
  private PlayerCharacterEntity character;

  @Column( name = "narrative_summary" )
  private String narrativeSummary;

  @Builder.Default
  @Column( name = "summary_version", nullable = false )
  private int summaryVersion = 0;

  @Builder.Default
  @Column( name = "turn_count", nullable = false )
  private int turnCount = 0;

  @Builder.Default
  @Column( name = "last_summary_at_turn", nullable = false )
  private int lastSummaryAtTurn = 0;

  @Column( name = "current_location" )
  private String currentLocation;

  @Column( name = "game_date" )
  private String gameDate;

  @Column( name = "game_time" )
  private String gameTime;

  private String meteo;

  private String temperature;

  @CreationTimestamp
  @Column( nullable = false, updatable = false )
  private LocalDateTime created;

  @UpdateTimestamp
  @Column( nullable = false )
  private LocalDateTime updated;
}
