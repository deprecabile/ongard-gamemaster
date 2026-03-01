package com.ongard.game.chat.service;

import com.ongard.game.chat.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class RaceServiceTest extends TestcontainersConfiguration {

  private @Autowired RaceService srv;

  @Test
  void testGetRaces() {
    assertThat(srv.getAllRaces()).hasSize(9);
  }

  @Test
  void testGetRaceByCode() {
    assertThat(srv.getRace("UMN").getName()).isEqualTo("Umano");
  }

}