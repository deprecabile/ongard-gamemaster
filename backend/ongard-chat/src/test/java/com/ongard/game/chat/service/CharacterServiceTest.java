package com.ongard.game.chat.service;

import com.ongard.game.chat.TestcontainersConfiguration;
import com.ongard.game.chat.contract.PlayerCharacterSaveRequest;
import com.ongard.game.chat.model.GameRace;
import com.ongard.game.chat.model.PlayerCharacter;
import com.ongard.game.exception.BadRequestException;
import com.ongard.game.header.GameUserHeader;
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

    PlayerCharacter created = characterService.createCharacter(request, POSTMAN_HEADER);
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

    assertThatThrownBy(() -> characterService.createCharacter(request, POSTMAN_HEADER))
        .isInstanceOf(BadRequestException.class);
  }
}
