package com.ondgard.game.chat.controller;

import com.google.gson.Gson;
import com.ondgard.game.GameGsonFactory;
import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.client.AccountClient;
import com.ondgard.game.chat.contract.UserCreateRequest;
import com.ondgard.game.chat.repository.ChatUserRepository;
import com.ondgard.game.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest extends TestcontainersConfiguration {

  @Autowired private MockMvc mockMvc;
  @Autowired private ChatUserRepository chatUserRepository;

  @MockitoBean
  private AccountClient accountClient;

  private final Gson gson = GameGsonFactory.build();

  @BeforeEach
  void setUp() {
    doNothing().when(accountClient).initUserLimits(anyString());
  }

  @Test
  void createUser_returns201AndPersistsUser() throws Exception {
    UUID userHash = UUID.randomUUID();

    mockMvc.perform(post("/api/internal/user")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new UserCreateRequest(userHash, "newplayer"))))
        .andExpect(status().isCreated());

    assertThat(chatUserRepository.findByUserHash(userHash)).isPresent();
  }

  @Test
  void createUser_callsInitUserLimits() throws Exception {
    UUID userHash = UUID.randomUUID();

    mockMvc.perform(post("/api/internal/user")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new UserCreateRequest(userHash, "limitsplayer"))))
        .andExpect(status().isCreated());

    verify(accountClient).initUserLimits(userHash.toString());
  }

  @Test
  void createUser_doesNotPersistUser_whenAccountServiceFails() throws Exception {
    doThrow(new AppException("Account service down"))
        .when(accountClient).initUserLimits(anyString());

    UUID userHash = UUID.randomUUID();

    mockMvc.perform(post("/api/internal/user")
            .contentType(MediaType.APPLICATION_JSON)
            .content(gson.toJson(new UserCreateRequest(userHash, "failplayer"))))
        .andExpect(status().isInternalServerError());

    assertThat(chatUserRepository.findByUserHash(userHash)).isEmpty();
  }
}
