package com.ondgard.game.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table( name = "password_reset", schema = "auth" )
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetEntity {

  @Id
  @GeneratedValue( strategy = GenerationType.IDENTITY )
  private Long id;

  @Column( name = "token_hash", nullable = false, unique = true, length = 64 )
  private String tokenHash;

  @ManyToOne( fetch = FetchType.LAZY )
  @JoinColumn( name = "user_id", nullable = false )
  private AppUserEntity user;

  @Column( nullable = false )
  private LocalDateTime expiry;
}
