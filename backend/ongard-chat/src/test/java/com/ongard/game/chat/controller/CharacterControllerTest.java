package com.ongard.game.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.ongard.game.GameGsonFactory;
import com.ongard.game.chat.TestcontainersConfiguration;
import com.ongard.game.chat.contract.PlayerCharacterSaveRequest;
import com.ongard.game.chat.model.GameRace;
import com.ongard.game.chat.service.RaceService;
import com.ongard.game.header.GameUserHeader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CharacterControllerTest extends TestcontainersConfiguration {

  @Autowired private MockMvc mockMvc;
  @Autowired private RaceService raceService;

  private final Gson gson = GameGsonFactory.build();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String POSTMAN_USER_HASH = "997a7552-5c1b-42c6-a0c0-094c50a0ad53";
  private static final String POSTMAN_USERNAME = "postman";

  private String buildUserHeader() throws Exception {
    GameUserHeader header = GameUserHeader.builder()
        .userId(POSTMAN_USER_HASH)
        .username(POSTMAN_USERNAME)
        .build();
    return objectMapper.writeValueAsString(header);
  }

  @Test
  void createCharacter_returns201WithBody() throws Exception {
    GameRace elf = raceService.getRace("ELF");
    PlayerCharacterSaveRequest request = new PlayerCharacterSaveRequest(elf, "Legolas", "An elven archer");

    mockMvc.perform(post("/api/character")
            .contentType(MediaType.APPLICATION_JSON)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader())
            .content(gson.toJson(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.characterHash", not(emptyString())))
        .andExpect(jsonPath("$.name").value("Legolas"))
        .andExpect(jsonPath("$.race.code").value("ELF"))
        .andExpect(jsonPath("$.userHash").value(POSTMAN_USER_HASH));
  }

  @Test
  void getAllCharacters_returnsCreatedCharacters() throws Exception {
    GameRace orc = raceService.getRace("ORC");
    PlayerCharacterSaveRequest request = new PlayerCharacterSaveRequest(orc, "Thrall", "An orc warchief");

    mockMvc.perform(post("/api/character")
            .contentType(MediaType.APPLICATION_JSON)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader())
            .content(gson.toJson(request)))
        .andExpect(status().isCreated());

    mockMvc.perform(get("/api/character/all")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", not(empty())))
        .andExpect(jsonPath("$[*].name", hasItem("Thrall")));
  }

  @Test
  void getCharacter_notFound_returns204() throws Exception {
    mockMvc.perform(get("/api/character/nonexistent_hash")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isNoContent());
  }

  @Test
  void createCharacter_invalidRace_returns400() throws Exception {
    GameRace fakeRace = GameRace.builder().code("INVALID").build();
    PlayerCharacterSaveRequest request = new PlayerCharacterSaveRequest(fakeRace, "Nobody", "Invalid");

    mockMvc.perform(post("/api/character")
            .contentType(MediaType.APPLICATION_JSON)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader())
            .content(gson.toJson(request)))
        .andExpect(status().isBadRequest());
  }
}
