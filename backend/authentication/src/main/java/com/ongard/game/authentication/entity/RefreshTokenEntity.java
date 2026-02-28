package com.ongard.game.authentication.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table( name = "refresh_token", schema = "auth" )
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenEntity {

  @Id
  @GeneratedValue( strategy = GenerationType.IDENTITY )
  private Long id;

  @ManyToOne( fetch = FetchType.LAZY )
  @JoinColumn( name = "user_id", nullable = false )
  private AppUserEntity user;

  @Column( nullable = false, unique = true )
  private String token;

  @Column( name = "expires_at", nullable = false )
  private LocalDateTime expiresAt;

  @Column( nullable = false )
  private LocalDateTime created;

  @Column( nullable = false )
  private Boolean revoked;
}
