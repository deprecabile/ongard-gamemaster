package com.ondgard.game.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table( name = "advisor_log" )
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdvisorLogEntity {

  @Id
  @GeneratedValue( strategy = GenerationType.IDENTITY )
  private Long id;

  @ManyToOne( fetch = FetchType.LAZY )
  @JoinColumn( name = "campaign_id", nullable = false )
  private CampaignEntity campaign;

  @Column( name = "turn_number", nullable = false )
  private int turnNumber;

  @Column( name = "user_message", nullable = false )
  private String userMessage;

  @Column( name = "advisor_response", nullable = false )
  private String advisorResponse;

  @CreationTimestamp
  @Column( nullable = false, updatable = false )
  private LocalDateTime created;
}
