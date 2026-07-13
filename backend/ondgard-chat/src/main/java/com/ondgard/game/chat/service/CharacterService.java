package com.ondgard.game.chat.service;

import com.ondgard.game.chat.contract.PlayerCharacterSaveRequest;
import com.ondgard.game.chat.error.GameErrorCode;
import com.ondgard.game.chat.model.GameRace;
import com.ondgard.game.chat.model.PlayerCharacter;
import com.ondgard.game.chat.repository.PlayerCharacterRepository;
import com.ondgard.game.chat.repository.projection.PlayerCharacterProjection;
import com.ondgard.game.exception.BadRequestException;
import com.ondgard.game.exception.NoResultException;
import com.ondgard.game.header.GameUserHeader;
import com.ondgard.game.tool.HashGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CharacterService {

  private final PlayerCharacterRepository playerCharacterRepository;
  private final RaceService raceService;

  public Collection<PlayerCharacter> getAllCharacters(GameUserHeader userHeader) {
    UUID userHash = UUID.fromString(userHeader.getUserId());
    return playerCharacterRepository.findAllByUserHash(userHash)
        .stream()
        .map(this::toModel)
        .toList();
  }

  public PlayerCharacter getCharacter(GameUserHeader userHeader, String characterHash) {
    UUID userHash = UUID.fromString(userHeader.getUserId());
    PlayerCharacterProjection projection = playerCharacterRepository
        .findByCharacterHashAndUserHash(characterHash, userHash)
        .orElseThrow(NoResultException::new);
    return toModel(projection);
  }

  @Transactional
  public PlayerCharacter createCharacter(GameUserHeader userHeader, PlayerCharacterSaveRequest request) {
    final String raceCode = request.getRace().getCode();
    final GameRace gameRace = raceService.getRace(raceCode);
    if( gameRace == null ){
      throw new BadRequestException(GameErrorCode.INVALID_RACE_CODE.getCode(), "Invalid race code: " + raceCode);
    }

    final UUID userHash = UUID.fromString(userHeader.getUserId());
    final String characterHash = HashGenerator.generateHash();

    playerCharacterRepository.insertCharacter(userHash, raceCode, characterHash,
        request.getName().trim(), request.getDescription().trim());

    log.info("Successfully created character {} for user: {}, {}", request.getName(), userHeader.getUsername(), userHeader.getUserId());
    return playerCharacterRepository.findByCharacterHashAndUserHash(characterHash, userHash)
        .map(this::toModel)
        .orElseThrow(NoResultException::new);
  }

  // ************************************ private ************************************

  private PlayerCharacter toModel(PlayerCharacterProjection projection) {
    GameRace race = raceService.getRace(projection.raceCode());
    return PlayerCharacter.builder()
        .characterHash(projection.characterHash())
        .userHash(projection.userHash())
        .race(race)
        .name(projection.name())
        .description(projection.description())
        .created(projection.created())
        .build();
  }
}
