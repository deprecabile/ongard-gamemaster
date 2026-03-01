package com.ongard.game.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table( name = "player_character" )
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerCharacterEntity {

  @Id
  @GeneratedValue( strategy = GenerationType.IDENTITY )
  private Long id;

  @ManyToOne( fetch = FetchType.LAZY )
  @JoinColumn( name = "user_id", nullable = false )
  private ChatUserEntity user;

  @ManyToOne( fetch = FetchType.LAZY )
  @JoinColumn( name = "race_id", nullable = false )
  private CfgRaceEntity race;

  @Column( name = "character_hash", nullable = false, unique = true )
  private String characterHash;

  @Column( nullable = false )
  private String name;

  @Column( nullable = false )
  private String description;

  @CreationTimestamp
  @Column( nullable = false, updatable = false )
  private LocalDateTime created;
}
