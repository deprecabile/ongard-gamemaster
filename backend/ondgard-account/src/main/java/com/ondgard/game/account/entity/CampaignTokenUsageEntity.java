package com.ondgard.game.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table( name = "campaign_token_usage" )
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignTokenUsageEntity {

  @Id
  @Column( name = "character_hash", nullable = false )
  private String characterHash;

  @Column( name = "user_hash", nullable = false )
  private String userHash;

  @Builder.Default
  @Column( name = "total_tokens", nullable = false )
  private long totalTokens = 0;

  @Builder.Default
  @Column( name = "month_tokens", nullable = false )
  private long monthTokens = 0;

  @Column( name = "last_reset_month", nullable = false )
  private String lastResetMonth;

  @UpdateTimestamp
  @Column( nullable = false )
  private Instant updated;
}
