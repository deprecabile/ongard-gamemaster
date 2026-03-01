package com.ongard.game.chat.controller;

import com.ongard.game.chat.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConfigControllerTest extends TestcontainersConfiguration {

  public static final Set<String> EXPECTED_CODES = Set.of("UMN", "ELF", "NAN", "GNO", "ORC", "TRO", "GOB", "RET", "XER");
  @Autowired private MockMvc mockMvc;

  @Test
  void getRaces_returnsAllRaces() throws Exception {
    mockMvc.perform(get("/api/config/races"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(EXPECTED_CODES.size())));
  }

  @Test
  void getRaces_eachRaceHasRequiredFields() throws Exception {
    mockMvc.perform(get("/api/config/races"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].code", everyItem(matchesPattern("[A-Z]{3}"))))
        .andExpect(jsonPath("$[*].name", everyItem(not(emptyString()))))
        .andExpect(jsonPath("$[*].description", everyItem(not(emptyString()))))
        .andExpect(jsonPath("$[*].attributes.minHeight", everyItem(notNullValue())))
        .andExpect(jsonPath("$[*].attributes.maxHeight", everyItem(notNullValue())))
        .andExpect(jsonPath("$[*].attributes.favoriteBiomes", everyItem(not(empty()))));
  }

  @Test
  void getRaces_containsExpectedRaceCodes() throws Exception {
    mockMvc.perform(get("/api/config/races"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].code", containsInAnyOrder(EXPECTED_CODES.toArray())));
  }
}
