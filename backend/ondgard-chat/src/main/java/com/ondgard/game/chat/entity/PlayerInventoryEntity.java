package com.ondgard.game.chat.entity;

import com.ondgard.game.chat.model.inventory.Inventory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Mutability;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table( name = "player_inventory" )
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerInventoryEntity {

  @Id
  @GeneratedValue( strategy = GenerationType.IDENTITY )
  private Long id;

  @ManyToOne( fetch = FetchType.LAZY )
  @JoinColumn( name = "campaign_id", nullable = false )
  private CampaignEntity campaign;

  @Column( nullable = false )
  private int version;

  @Column( name = "turn_number", nullable = false )
  private int turnNumber;

  @JdbcTypeCode( SqlTypes.JSON )
  @Mutability( JsonMutabilityPlan.class )
  @Column( nullable = false, columnDefinition = "jsonb" )
  private Inventory inventory;

  @CreationTimestamp
  @Column( nullable = false, updatable = false )
  private LocalDateTime created;
}
