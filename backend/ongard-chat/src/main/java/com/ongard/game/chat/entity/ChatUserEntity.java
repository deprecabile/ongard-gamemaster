package com.ongard.game.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table( name = "game_user" )
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatUserEntity {

  @Id
  @GeneratedValue( strategy = GenerationType.IDENTITY )
  private Long id;

  @Column( name = "user_hash", nullable = false, unique = true )
  private UUID userHash;

  @Column( nullable = false, unique = true )
  private String username;

  @CreationTimestamp
  @Column( nullable = false, updatable = false )
  private LocalDateTime created;
}
