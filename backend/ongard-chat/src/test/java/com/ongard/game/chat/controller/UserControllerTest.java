package com.ongard.game.chat.controller;

import com.google.gson.Gson;
import com.ongard.game.GameGsonFactory;
import com.ongard.game.chat.TestcontainersConfiguration;
import com.ongard.game.chat.contract.UserCreateRequest;
import com.ongard.game.chat.repository.ChatUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest extends TestcontainersConfiguration {

    @Autowired private MockMvc mockMvc;
    @Autowired private ChatUserRepository chatUserRepository;

    private final Gson gson = GameGsonFactory.build();

    @Test
    void createUser_returns201AndPersistsUser() throws Exception {
        UUID userHash = UUID.randomUUID();

        mockMvc.perform(post("/api/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(new UserCreateRequest(userHash, "newplayer"))))
                .andExpect(status().isCreated());

        assertThat(chatUserRepository.findByUserHash(userHash)).isPresent();
    }
}
