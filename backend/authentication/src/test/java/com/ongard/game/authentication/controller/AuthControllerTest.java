package com.ongard.game.authentication.controller;

import com.jayway.jsonpath.JsonPath;
import com.ongard.game.authentication.TestcontainersConfiguration;
import com.ongard.game.authentication.client.ChatClient;
import com.ongard.game.authentication.entity.AppUserEntity;
import com.ongard.game.authentication.entity.RefreshTokenEntity;
import com.ongard.game.authentication.repository.AppUserRepository;
import com.ongard.game.authentication.repository.RefreshTokenRepository;
import com.ongard.game.authentication.scheduled.TokenCleanupTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest extends TestcontainersConfiguration {

  private static final String PEPPER = "XkP7$mQ2nW";
  private static final String TEST_PASSWORD = "Secret123";
  private static final String TEST_SALT = "test-salt-uuid";

  @Autowired private MockMvc mockMvc;
  @Autowired private AppUserRepository appUserRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private Argon2PasswordEncoder passwordEncoder;

  @MockitoBean private ChatClient chatClient;

  private AppUserEntity testUser;

  @BeforeEach
  void setUp() {
    refreshTokenRepository.deleteAll();
    // Keep seed user, delete any others we created
    appUserRepository.findAll().stream()
        .filter(u -> !"postman".equals(u.getUsername()))
        .forEach(appUserRepository::delete);

    doNothing().when(chatClient).createUser(any(), anyString());

    // Create a test user with known password
    testUser = appUserRepository.save(AppUserEntity.builder()
        .username("testuser")
        .email("test@aigame.it")
        .passwordHash(passwordEncoder.encode(PEPPER + TEST_PASSWORD + TEST_SALT))
        .salt(TEST_SALT)
        .enabled(true)
        .created(LocalDateTime.now())
        .build());
    // Re-read to get DB-generated userHash
    testUser = appUserRepository.findByUsername("testuser").orElseThrow();
  }

  private String loginJson(String username, String password) {
    return """
        {"username":"%s","password":"%s"}""".formatted(username, password);
  }

  private String registerJson(String username, String email, String password) {
    return """
        {"username":"%s","email":"%s","password":"%s"}""".formatted(username, email, password);
  }

  private String refreshJson(String username, String refreshToken) {
    return """
        {"username":"%s","refreshToken":"%s"}""".formatted(username, refreshToken);
  }

  // ── POST /api/auth/login ───────────────────────────────────────────

  @Nested
  class Login {

    @Test
    void returnsTokensOnValidCredentials() throws Exception {
      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(loginJson("testuser", TEST_PASSWORD)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken", notNullValue()))
          .andExpect(jsonPath("$.refreshToken", notNullValue()))
          .andExpect(jsonPath("$.expiresIn").value(1800));
    }

    @Test
    void updatesLastLoginTimestamp() throws Exception {
      LocalDateTime before = LocalDateTime.now().minusSeconds(1);

      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(loginJson("testuser", TEST_PASSWORD)))
          .andExpect(status().isOk());

      AppUserEntity updated = appUserRepository.findByUsername("testuser").orElseThrow();
      assertThat(updated.getLastLogin()).isAfter(before);
    }

    @Test
    void returns401ForWrongPassword() throws Exception {
      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(loginJson("testuser", "WrongPassword")))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.messages[0].code").value("AU_401_01"));
    }

    @Test
    void returns401ForNonExistentUser() throws Exception {
      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(loginJson("ghost", "anything")))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.messages[0].code").value("AU_401_01"));
    }

    @Test
    void returns401ForDisabledUser() throws Exception {
      testUser.setEnabled(false);
      appUserRepository.save(testUser);

      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(loginJson("testuser", TEST_PASSWORD)))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void returns401ForLockedUser() throws Exception {
      testUser.setLockedUntil(LocalDateTime.now().plusHours(1));
      appUserRepository.save(testUser);

      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(loginJson("testuser", TEST_PASSWORD)))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void returns400ForBlankFields() throws Exception {
      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(loginJson("", "")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messages").isNotEmpty());
    }
  }

  // ── POST /api/auth/register ────────────────────────────────────────

  @Nested
  class Register {

    @Test
    void returns201AndCreatesUser() throws Exception {
      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("newplayer", "new@aigame.it", "Pass123")))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.username").value("newplayer"))
          .andExpect(jsonPath("$.email").value("new@aigame.it"))
          .andExpect(jsonPath("$.userHash", notNullValue()));

      assertThat(appUserRepository.findByUsername("newplayer")).isPresent();
    }

    @Test
    void registeredUserCanLogin() throws Exception {
      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("loginable", "loginable@aigame.it", "MyPass1")))
          .andExpect(status().isCreated());

      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(loginJson("loginable", "MyPass1")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    void returns400ForDuplicateUsername() throws Exception {
      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("testuser", "other@aigame.it", "Pass123")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messages[0].code").value("AU_400_01"));
    }

    @Test
    void returns400ForDuplicateEmail() throws Exception {
      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("unique", "test@aigame.it", "Pass123")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messages[0].code").value("AU_400_02"));
    }

    @Test
    void returns400ForInvalidEmail() throws Exception {
      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("valid", "not-an-email", "Pass123")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messages[0].code").value("AU_400_00"));
    }

    @Test
    void returns400ForShortUsername() throws Exception {
      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("ab", "short@aigame.it", "Pass123")))
          .andExpect(status().isBadRequest());
    }

    @Test
    void returns400ForShortPassword() throws Exception {
      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("validname", "pwd@aigame.it", "12345")))
          .andExpect(status().isBadRequest());
    }
  }

  // ── POST /api/auth/login/refresh ───────────────────────────────────

  @Nested
  class RefreshToken {

    private String obtainRefreshToken() throws Exception {
      MvcResult result = mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(loginJson("testuser", TEST_PASSWORD)))
          .andReturn();
      return JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");
    }

    @Test
    void returnsNewTokensOnValidRefresh() throws Exception {
      String refreshToken = obtainRefreshToken();

      mockMvc.perform(post("/api/auth/login/refresh")
              .contentType(MediaType.APPLICATION_JSON)
              .content(refreshJson("testuser", refreshToken)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken", notNullValue()))
          .andExpect(jsonPath("$.refreshToken", notNullValue()))
          .andExpect(jsonPath("$.expiresIn").value(1800));
    }

    @Test
    void oldTokenIsRevokedAfterRotation() throws Exception {
      String refreshToken = obtainRefreshToken();

      mockMvc.perform(post("/api/auth/login/refresh")
              .contentType(MediaType.APPLICATION_JSON)
              .content(refreshJson("testuser", refreshToken)))
          .andExpect(status().isOk());

      // Using the old token again should fail
      mockMvc.perform(post("/api/auth/login/refresh")
              .contentType(MediaType.APPLICATION_JSON)
              .content(refreshJson("testuser", refreshToken)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.messages[0].code").value("AU_401_02"));
    }

    @Test
    void returns401ForExpiredRefreshToken() throws Exception {
      String refreshToken = obtainRefreshToken();

      // Expire the token manually
      RefreshTokenEntity entity = refreshTokenRepository
          .findByTokenAndRevokedFalseAndUser_Username(refreshToken, "testuser")
          .orElseThrow();
      entity.setExpiresAt(LocalDateTime.now().minusSeconds(1));
      refreshTokenRepository.save(entity);

      mockMvc.perform(post("/api/auth/login/refresh")
              .contentType(MediaType.APPLICATION_JSON)
              .content(refreshJson("testuser", refreshToken)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.messages[0].code").value("AU_401_03"));
    }

    @Test
    void returns401ForInvalidToken() throws Exception {
      mockMvc.perform(post("/api/auth/login/refresh")
              .contentType(MediaType.APPLICATION_JSON)
              .content(refreshJson("testuser", "bogus-token")))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.messages[0].code").value("AU_401_02"));
    }

    @Test
    void returns400ForBlankFields() throws Exception {
      mockMvc.perform(post("/api/auth/login/refresh")
              .contentType(MediaType.APPLICATION_JSON)
              .content(refreshJson("", "")))
          .andExpect(status().isBadRequest());
    }
  }

  // ── GET /api/auth/check-username ───────────────────────────────────

  @Nested
  class CheckUsername {

    @Test
    void returnsTrueForAvailableUsername() throws Exception {
      mockMvc.perform(get("/api/auth/check-username")
              .param("username", "available"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.available", is(true)));
    }

    @Test
    void returnsFalseForTakenUsername() throws Exception {
      mockMvc.perform(get("/api/auth/check-username")
              .param("username", "testuser"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.available", is(false)));
    }
  }

  // ── TokenCleanupTask ───────────────────────────────────────────────

  @Nested
  class TokenCleanup {

    @Autowired
    private TokenCleanupTask tokenCleanupTask;

    @Test
    void deletesExpiredAndRevokedTokens() throws Exception {
      // Login to create a valid token
      MvcResult result = mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(loginJson("testuser", TEST_PASSWORD)))
          .andReturn();
      String refreshToken = JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");

      // Create an expired token
      refreshTokenRepository.save(RefreshTokenEntity.builder()
          .user(testUser)
          .token("expired-token")
          .expiresAt(LocalDateTime.now().minusDays(1))
          .created(LocalDateTime.now().minusDays(2))
          .revoked(false)
          .build());

      // Create a revoked token
      refreshTokenRepository.save(RefreshTokenEntity.builder()
          .user(testUser)
          .token("revoked-token")
          .expiresAt(LocalDateTime.now().plusDays(1))
          .created(LocalDateTime.now())
          .revoked(true)
          .build());

      assertThat(refreshTokenRepository.findAll()).hasSize(3);

      tokenCleanupTask.cleanupExpiredTokens();

      // Only the valid (non-expired, non-revoked) token should remain
      assertThat(refreshTokenRepository.findAll()).hasSize(1);
      assertThat(refreshTokenRepository.findByTokenAndRevokedFalseAndUser_Username(refreshToken, "testuser"))
          .isPresent();
    }
  }
}
