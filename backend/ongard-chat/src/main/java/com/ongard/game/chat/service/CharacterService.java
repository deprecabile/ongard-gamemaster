package com.ongard.game.chat.service;

import com.ongard.game.chat.contract.PlayerCharacterSaveRequest;
import com.ongard.game.chat.error.GameErrorCode;
import com.ongard.game.chat.model.GameRace;
import com.ongard.game.chat.model.PlayerCharacter;
import com.ongard.game.chat.repository.PlayerCharacterRepository;
import com.ongard.game.chat.repository.projection.PlayerCharacterProjection;
import com.ongard.game.exception.BadRequestException;
import com.ongard.game.exception.NoResultException;
import com.ongard.game.header.GameUserHeader;
import com.ongard.game.tool.HashGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

@Service
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

  public PlayerCharacter getCharacter(String characterHash, GameUserHeader userHeader) {
    UUID userHash = UUID.fromString(userHeader.getUserId());
    PlayerCharacterProjection projection = playerCharacterRepository
        .findByCharacterHashAndUserHash(characterHash, userHash)
        .orElseThrow(NoResultException::new);
    return toModel(projection);
  }

  @Transactional
  public PlayerCharacter createCharacter(PlayerCharacterSaveRequest request,
                                         GameUserHeader userHeader) {
    final String raceCode = request.getRace().getCode();
    final GameRace gameRace = raceService.getRace(raceCode);
    if( gameRace == null ){
      throw new BadRequestException(GameErrorCode.INVALID_RACE_CODE.getCode(), "Invalid race code: " + raceCode);
    }

    final UUID userHash = UUID.fromString(userHeader.getUserId());
    final String characterHash = HashGenerator.generateHash();

    playerCharacterRepository.insertCharacter(userHash, raceCode, characterHash,
        request.getName(), request.getDescription());

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
