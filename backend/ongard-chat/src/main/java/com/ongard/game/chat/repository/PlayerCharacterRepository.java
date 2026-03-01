package com.ongard.game.chat.repository;

import com.ongard.game.chat.entity.PlayerCharacterEntity;
import com.ongard.game.chat.repository.projection.PlayerCharacterProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlayerCharacterRepository extends JpaRepository<PlayerCharacterEntity, Long> {

  @Query( """
      SELECT new com.ongard.game.chat.repository.projection.PlayerCharacterProjection(
          pc.characterHash, u.userHash, r.code,
          pc.name, pc.description, pc.created
      )
      FROM PlayerCharacterEntity pc
      JOIN pc.user u
      JOIN pc.race r
      WHERE u.userHash = :userHash
      """ )
  Collection<PlayerCharacterProjection> findAllByUserHash(@Param( "userHash" ) UUID userHash);

  @Query( """
      SELECT new com.ongard.game.chat.repository.projection.PlayerCharacterProjection(
          pc.characterHash, u.userHash, r.code,
          pc.name, pc.description, pc.created
      )
      FROM PlayerCharacterEntity pc
      JOIN pc.user u
      JOIN pc.race r
      WHERE pc.characterHash = :characterHash AND u.userHash = :userHash
      """ )
  Optional<PlayerCharacterProjection> findByCharacterHashAndUserHash(
      @Param( "characterHash" ) String characterHash,
      @Param( "userHash" ) UUID userHash);

  @Modifying
  @Query( value = """
      INSERT INTO player_character (user_id, race_id, character_hash, name, description, created)
      VALUES (
          (SELECT gu.id FROM game_user gu WHERE gu.user_hash = :userHash),
          (SELECT cr.id FROM cfg_race cr WHERE cr.code = :raceCode),
          :characterHash,
          :name,
          :description,
          NOW()
      )
      """, nativeQuery = true )
  void insertCharacter(@Param( "userHash" ) UUID userHash,
                       @Param( "raceCode" ) String raceCode,
                       @Param( "characterHash" ) String characterHash,
                       @Param( "name" ) String name,
                       @Param( "description" ) String description);
}
