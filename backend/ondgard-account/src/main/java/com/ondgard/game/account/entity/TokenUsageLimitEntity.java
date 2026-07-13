package com.ondgard.game.account.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table( name = "token_usage_limit" )
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenUsageLimitEntity {

  @Id
  @GeneratedValue( strategy = GenerationType.IDENTITY )
  private Long id;

  @Column( name = "user_hash", nullable = false )
  private String userHash;

  @Column( name = "limit_month", nullable = false )
  private long limitMonth;

  @Column( name = "limit_total", nullable = false )
  private long limitTotal;

  @Column( nullable = false )
  private int version;

  @CreationTimestamp
  @Column( nullable = false, updatable = false )
  private Instant created;

  @UpdateTimestamp
  @Column( nullable = false )
  private Instant updated;
}
