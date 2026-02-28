package com.ongard.game.authentication.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table( name = "app_user", schema = "auth" )
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUserEntity {

  @Id
  @GeneratedValue( strategy = GenerationType.IDENTITY )
  private Long id;

  @Generated( event = EventType.INSERT )
  @Column( name = "user_hash", nullable = false, unique = true, insertable = false, updatable = false )
  private UUID userHash;

  @Column( nullable = false, updatable = false )
  private LocalDateTime created;

  @Column( nullable = false, unique = true )
  private String username;

  @Column( nullable = false, unique = true )
  private String email;

  @Column( name = "password_hash", nullable = false )
  private String passwordHash;

  @Column( nullable = false )
  private String salt;

  @Column( nullable = false )
  private Boolean enabled;

  @Column( name = "locked_until" )
  private LocalDateTime lockedUntil;

  @Column( name = "last_login" )
  private LocalDateTime lastLogin;

}
