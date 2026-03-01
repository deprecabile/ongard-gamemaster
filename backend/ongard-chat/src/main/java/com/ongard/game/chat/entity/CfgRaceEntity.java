package com.ongard.game.chat.entity;

import com.ongard.game.chat.model.RaceAttributes;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table( name = "cfg_race" )
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CfgRaceEntity {

  @Id
  @GeneratedValue( strategy = GenerationType.IDENTITY )
  private Long id;

  @Column( nullable = false, unique = true )
  private String code;

  @Column( nullable = false )
  private String name;

  @Column( nullable = false )
  private String description;

  @JdbcTypeCode( SqlTypes.JSON )
  @Column( nullable = false, columnDefinition = "jsonb" )
  private RaceAttributes attributes;
}
