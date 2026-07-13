package com.ondgard.game.chat.service;

import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.contract.PlayerCharacterSaveRequest;
import com.ondgard.game.chat.model.GameRace;
import com.ondgard.game.chat.model.PlayerCharacter;
import com.ondgard.game.exception.BadRequestException;
import com.ondgard.game.header.GameUserHeader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CharacterServiceTest extends TestcontainersConfiguration {

  @Autowired private CharacterService characterService;
  @Autowired private RaceService raceService;

  private static final GameUserHeader POSTMAN_HEADER = GameUserHeader.builder()
      .userId("997a7552-5c1b-42c6-a0c0-094c50a0ad53")
      .username("postman")
      .build();

  @Test
  void createAndRetrieveCharacter() {
    GameRace race = raceService.getRace("NAN");
    PlayerCharacterSaveRequest request = new PlayerCharacterSaveRequest(race, "Gimli", "A dwarven warrior");

    PlayerCharacter created = characterService.createCharacter(POSTMAN_HEADER, request);
    assertThat(created.getCharacterHash()).hasSize(32);
    assertThat(created.getName()).isEqualTo("Gimli");
    assertThat(created.getRace().getCode()).isEqualTo("NAN");

    Collection<PlayerCharacter> all = characterService.getAllCharacters(POSTMAN_HEADER);
    assertThat(all).extracting(PlayerCharacter::getName).contains("Gimli");
  }

  @Test
  void createCharacter_invalidRace_throwsBadRequest() {
    GameRace fakeRace = GameRace.builder().code("INVALID").build();
    PlayerCharacterSaveRequest request = new PlayerCharacterSaveRequest(fakeRace, "Nobody", "Invalid");

    assertThatThrownBy(() -> characterService.createCharacter(POSTMAN_HEADER, request)).isInstanceOf(BadRequestException.class);
  }
}
