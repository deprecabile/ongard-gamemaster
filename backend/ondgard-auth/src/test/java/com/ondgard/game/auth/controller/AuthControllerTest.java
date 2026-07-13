package com.ondgard.game.auth.controller;

import com.jayway.jsonpath.JsonPath;
import com.ondgard.game.auth.TestcontainersConfiguration;
import com.ondgard.game.auth.client.ChatClient;
import com.ondgard.game.auth.client.MailClient;
import com.ondgard.game.auth.entity.AppUserEntity;
import com.ondgard.game.auth.entity.EmailConfirmationEntity;
import com.ondgard.game.auth.entity.PasswordResetEntity;
import com.ondgard.game.auth.entity.RefreshTokenEntity;
import com.ondgard.game.auth.model.GoogleUserInfo;
import com.ondgard.game.auth.repository.AppUserRepository;
import com.ondgard.game.auth.repository.EmailConfirmationRepository;
import com.ondgard.game.auth.repository.PasswordResetRepository;
import com.ondgard.game.auth.repository.RefreshTokenRepository;
import com.ondgard.game.auth.scheduled.TokenCleanupTask;
import com.ondgard.game.auth.scheduled.UnconfirmedUserCleanupTask;
import com.ondgard.game.auth.service.GoogleTokenVerifier;
import com.ondgard.game.auth.util.TokenHashUtil;
import com.ondgard.game.contract.mail.SendMailRequest;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
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

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private AppUserRepository appUserRepository;
  @Autowired
  private RefreshTokenRepository refreshTokenRepository;
  @Autowired
  private EmailConfirmationRepository emailConfirmationRepository;
  @Autowired
  private PasswordResetRepository passwordResetRepository;
  @Autowired
  private Argon2PasswordEncoder passwordEncoder;

  @MockitoBean
  private ChatClient chatClient;
  @MockitoBean
  private MailClient mailClient;
  @MockitoBean
  private GoogleTokenVerifier googleTokenVerifier;

  private AppUserEntity testUser;

  @BeforeEach
  void setUp() {
    passwordResetRepository.deleteAll();
    emailConfirmationRepository.deleteAll();
    refreshTokenRepository.deleteAll();
    // Keep seed user, delete any others we created
    appUserRepository.findAll().stream().filter(u -> !"postman".equals(u.getUsername())).forEach(appUserRepository::delete);

    doNothing().when(chatClient).createUser(any(), anyString());
    reset(mailClient);
    reset(googleTokenVerifier);

    // Create a test user with known password
    testUser = appUserRepository.save(AppUserEntity.builder().username("testuser").email("test@aigame.it").passwordHash(passwordEncoder.encode(PEPPER + TEST_PASSWORD + TEST_SALT)).salt(TEST_SALT).enabled(true).created(LocalDateTime.now()).build());
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

  private String confirmJson(UUID token) {
    return """
        {"token":"%s"}""".formatted(token);
  }

  private String forgotPasswordJson(String email) {
    return """
        {"email":"%s"}""".formatted(email);
  }

  private String resetPasswordJson(UUID token, String newPassword) {
    return """
        {"token":"%s","newPassword":"%s"}""".formatted(token, newPassword);
  }

  private String googleAuthJson(String credential, String username) {
    if( username != null ){
      return """
          {"credential":"%s","username":"%s"}""".formatted(credential, username);
    }
    return """
        {"credential":"%s"}""".formatted(credential);
  }

  // ── POST /api/auth/login ───────────────────────────────────────────

  @Nested
  class Login {

    @Test
    void returnsTokensOnValidCredentials() throws Exception {
      mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("testuser", TEST_PASSWORD))).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken", notNullValue())).andExpect(jsonPath("$.refreshToken", notNullValue())).andExpect(jsonPath("$.expiresIn").value(1800));
    }

    @Test
    void updatesLastLoginTimestamp() throws Exception {
      LocalDateTime before = LocalDateTime.now().minusSeconds(1);

      mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("testuser", TEST_PASSWORD))).andExpect(status().isOk());

      AppUserEntity updated = appUserRepository.findByUsername("testuser").orElseThrow();
      assertThat(updated.getLastLogin()).isAfter(before);
    }

    @Test
    void returns401ForWrongPassword() throws Exception {
      mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("testuser", "WrongPassword"))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.messages[0].code").value("AU_401_01"));
    }

    @Test
    void returns401ForNonExistentUser() throws Exception {
      mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("ghost", "anything"))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.messages[0].code").value("AU_401_01"));
    }

    @Test
    void returns403ForNotConfirmedUser() throws Exception {
      testUser.setEnabled(false);
      appUserRepository.save(testUser);

      mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("testuser", TEST_PASSWORD))).andExpect(status().isForbidden()).andExpect(jsonPath("$.messages[0].code").value("AU_403_01"));
    }

    @Test
    void returns401ForLockedUser() throws Exception {
      testUser.setLockedUntil(LocalDateTime.now().plusHours(1));
      appUserRepository.save(testUser);

      mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("testuser", TEST_PASSWORD))).andExpect(status().isUnauthorized());
    }

    @Test
    void returns400ForBlankFields() throws Exception {
      mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("", ""))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.messages").isNotEmpty());
    }
  }

  // ── POST /api/auth/register ────────────────────────────────────────

  @Nested
  class Register {

    @Test
    void scenarioA_newUser_createsDisabledUserAndSendsEmail() throws Exception {
      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("newplayer", "new@aigame.it", "Pass1234")))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.ok").value(true));

      AppUserEntity created = appUserRepository.findByUsername("newplayer").orElseThrow();
      assertThat(created.getEnabled()).isFalse();
      assertThat(emailConfirmationRepository.findByUser(created)).isPresent();
      verify(mailClient).sendEmail(any(SendMailRequest.class));
    }

    @Test
    void scenarioB_staleUsername_deletesOldUserAndCreatesNew() throws Exception {
      // Create stale user: disabled, token expired
      AppUserEntity stale = appUserRepository.save(AppUserEntity.builder()
          .username("staleuser").email("stale@aigame.it")
          .passwordHash("hash").salt("salt")
          .enabled(false).created(LocalDateTime.now())
          .build());
      emailConfirmationRepository.save(EmailConfirmationEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex("expired"))
          .user(stale)
          .expiry(LocalDateTime.now().minusHours(1))
          .build());

      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("staleuser", "fresh@aigame.it", "Pass1234")))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.ok").value(true));

      AppUserEntity created = appUserRepository.findByUsername("staleuser").orElseThrow();
      assertThat(created.getEmail()).isEqualTo("fresh@aigame.it");
      assertThat(created.getEnabled()).isFalse();
      verify(mailClient).sendEmail(any(SendMailRequest.class));
    }

    @Test
    void scenarioC_emailOfConfirmedUser_returns201AndSendsAlreadyRegistered() throws Exception {
      // testUser is enabled with email test@aigame.it
      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("otheruser", "test@aigame.it", "Pass1234")))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.ok").value(true));

      verify(mailClient).sendEmail(any(SendMailRequest.class));
      AppUserEntity updated = appUserRepository.findByUsername("testuser").orElseThrow();
      assertThat(updated.getLastEmailSentAt()).isNotNull();
    }

    @Test
    void scenarioC_throttled_doesNotSendEmail() throws Exception {
      // Set lastEmailSentAt to recent
      testUser.setLastEmailSentAt(LocalDateTime.now().minusMinutes(30));
      appUserRepository.save(testUser);

      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("otheruser", "test@aigame.it", "Pass1234")))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.ok").value(true));

      verify(mailClient, never()).sendEmail(any(SendMailRequest.class));
    }

    @Test
    void scenarioD_emailOfUnconfirmedUser_updatesCredentials() throws Exception {
      // Create unconfirmed user
      AppUserEntity unconfirmed = appUserRepository.save(AppUserEntity.builder()
          .username("oldname").email("unconfirmed@aigame.it")
          .passwordHash("oldhash").salt("oldsalt")
          .enabled(false).created(LocalDateTime.now())
          .build());
      emailConfirmationRepository.save(EmailConfirmationEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex("oldtoken"))
          .user(unconfirmed)
          .expiry(LocalDateTime.now().plusHours(12)) // not throttled (< now+23h)
          .build());

      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("newname", "unconfirmed@aigame.it", "NewPass12")))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.ok").value(true));

      // Username updated
      assertThat(appUserRepository.findByUsername("oldname")).isEmpty();
      AppUserEntity updated = appUserRepository.findByUsername("newname").orElseThrow();
      assertThat(updated.getEmail()).isEqualTo("unconfirmed@aigame.it");
      // Token hash changed
      EmailConfirmationEntity ec = emailConfirmationRepository.findByUser(updated).orElseThrow();
      assertThat(ec.getTokenHash()).isNotEqualTo(TokenHashUtil.sha256Hex("oldtoken"));
      verify(mailClient).sendEmail(any(SendMailRequest.class));
    }

    @Test
    void scenarioD_throttled_doesNotUpdate() throws Exception {
      // Create unconfirmed user with recently generated token (expiry > now+23h)
      AppUserEntity unconfirmed = appUserRepository.save(AppUserEntity.builder()
          .username("throttled").email("throttled@aigame.it")
          .passwordHash("hash").salt("salt")
          .enabled(false).created(LocalDateTime.now())
          .build());
      emailConfirmationRepository.save(EmailConfirmationEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex("recent"))
          .user(unconfirmed)
          .expiry(LocalDateTime.now().plusHours(24)) // > now+23h = throttled
          .build());

      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("throttled", "throttled@aigame.it", "NewPass12")))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.ok").value(true));

      verify(mailClient, never()).sendEmail(any(SendMailRequest.class));
    }

    @Test
    void scenarioD_usernameConflict_returns400() throws Exception {
      // Create unconfirmed user
      AppUserEntity unconfirmed = appUserRepository.save(AppUserEntity.builder()
          .username("oldname2").email("conflict@aigame.it")
          .passwordHash("hash").salt("salt")
          .enabled(false).created(LocalDateTime.now())
          .build());
      emailConfirmationRepository.save(EmailConfirmationEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex("tok"))
          .user(unconfirmed)
          .expiry(LocalDateTime.now().plusHours(12))
          .build());

      // Try to change username to "testuser" which is taken (enabled)
      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("testuser", "conflict@aigame.it", "Pass1234")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messages[0].code").value("AU_400_01"));
    }

    @Test
    void returns400ForDuplicateUsername() throws Exception {
      // testuser is enabled -> username taken
      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("testuser", "other@aigame.it", "Pass1234")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messages[0].code").value("AU_400_01"));
    }

    @Test
    void usernamePattern_rejectsSpecialChars() throws Exception {
      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("<script>", "xss@aigame.it", "Pass1234")))
          .andExpect(status().isBadRequest());
    }

    @Test
    void registeredUserCannotLoginUntilConfirmed() throws Exception {
      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(registerJson("loginable", "loginable@aigame.it", "MyPass12")))
          .andExpect(status().isCreated());

      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(loginJson("loginable", "MyPass12")))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.messages[0].code").value("AU_403_01"));
    }

    @Test
    void returns400ForInvalidEmail() throws Exception {
      mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerJson("valid", "not-an-email", "Pass1234"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.messages[0].code").value("AU_400_00"));
    }

    @Test
    void returns400ForShortUsername() throws Exception {
      mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerJson("ab", "short@aigame.it", "Pass1234"))).andExpect(status().isBadRequest());
    }

    @Test
    void returns400ForShortPassword() throws Exception {
      mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerJson("validname", "pwd@aigame.it", "12345"))).andExpect(status().isBadRequest());
    }
  }

  // ── POST /api/auth/login/refresh ───────────────────────────────────

  @Nested
  class RefreshToken {

    private String obtainRefreshToken() throws Exception {
      MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("testuser", TEST_PASSWORD))).andReturn();
      return JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");
    }

    @Test
    void returnsNewTokensOnValidRefresh() throws Exception {
      String refreshToken = obtainRefreshToken();

      mockMvc.perform(post("/api/auth/login/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshJson("testuser", refreshToken))).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken", notNullValue())).andExpect(jsonPath("$.refreshToken", notNullValue())).andExpect(jsonPath("$.expiresIn").value(1800));
    }

    @Test
    void oldTokenIsRevokedAfterRotation() throws Exception {
      String refreshToken = obtainRefreshToken();

      mockMvc.perform(post("/api/auth/login/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshJson("testuser", refreshToken))).andExpect(status().isOk());

      // Using the old token again should fail
      mockMvc.perform(post("/api/auth/login/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshJson("testuser", refreshToken))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.messages[0].code").value("AU_401_02"));
    }

    @Test
    void returns401ForExpiredRefreshToken() throws Exception {
      String refreshToken = obtainRefreshToken();

      // Expire the token manually
      RefreshTokenEntity entity = refreshTokenRepository.findActiveByHashAndUsername(TokenHashUtil.sha256Hex(refreshToken), "testuser").orElseThrow();
      entity.setExpiresAt(LocalDateTime.now().minusSeconds(1));
      refreshTokenRepository.save(entity);

      mockMvc.perform(post("/api/auth/login/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshJson("testuser", refreshToken))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.messages[0].code").value("AU_401_03"));
    }

    @Test
    void returns401ForInvalidToken() throws Exception {
      mockMvc.perform(post("/api/auth/login/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshJson("testuser", "bogus-token"))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.messages[0].code").value("AU_401_02"));
    }

    @Test
    void returns400ForBlankFields() throws Exception {
      mockMvc.perform(post("/api/auth/login/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshJson("", ""))).andExpect(status().isBadRequest());
    }

    @Test
    void refreshTokenIsStoredAsHashInDatabase() throws Exception {
      String refreshToken = obtainRefreshToken();

      String expectedHash = TokenHashUtil.sha256Hex(refreshToken);
      RefreshTokenEntity entity = refreshTokenRepository
          .findActiveByHashAndUsername(expectedHash, "testuser")
          .orElseThrow();

      assertThat(entity.getTokenHash()).hasSize(64);
      assertThat(entity.getTokenHash()).isEqualTo(expectedHash);
      assertThat(entity.getTokenHash()).isNotEqualTo(refreshToken);
    }
  }

  // ── GET /api/auth/check-username ───────────────────────────────────

  @Nested
  class CheckUsername {

    @Test
    void returnsTrueForAvailableUsername() throws Exception {
      mockMvc.perform(get("/api/auth/check-username").param("username", "available")).andExpect(status().isOk()).andExpect(jsonPath("$.available", is(true)));
    }

    @Test
    void returnsFalseForTakenUsername() throws Exception {
      mockMvc.perform(get("/api/auth/check-username").param("username", "testuser")).andExpect(status().isOk()).andExpect(jsonPath("$.available", is(false)));
    }

    @Test
    void returnsTrueForUnconfirmedUserWithExpiredToken() throws Exception {
      AppUserEntity unconfirmed = appUserRepository.save(AppUserEntity.builder()
          .username("ghost").email("ghost@aigame.it")
          .passwordHash("hash").salt("salt")
          .enabled(false).created(LocalDateTime.now())
          .build());
      emailConfirmationRepository.save(EmailConfirmationEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex("expired"))
          .user(unconfirmed)
          .expiry(LocalDateTime.now().minusHours(1))
          .build());

      mockMvc.perform(get("/api/auth/check-username").param("username", "ghost"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.available", is(true)));
    }

    @Test
    void returnsTrueForDisabledUserWithNoToken() throws Exception {
      appUserRepository.save(AppUserEntity.builder()
          .username("orphan").email("orphan@aigame.it")
          .passwordHash("hash").salt("salt")
          .enabled(false).created(LocalDateTime.now())
          .build());

      mockMvc.perform(get("/api/auth/check-username").param("username", "orphan"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.available", is(true)));
    }

    @Test
    void returnsFalseForUnconfirmedUserWithValidToken() throws Exception {
      AppUserEntity unconfirmed = appUserRepository.save(AppUserEntity.builder()
          .username("pending").email("pending@aigame.it")
          .passwordHash("hash").salt("salt")
          .enabled(false).created(LocalDateTime.now())
          .build());
      emailConfirmationRepository.save(EmailConfirmationEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex("valid"))
          .user(unconfirmed)
          .expiry(LocalDateTime.now().plusHours(23))
          .build());

      mockMvc.perform(get("/api/auth/check-username").param("username", "pending"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.available", is(false)));
    }
  }

  // ── POST /api/auth/confirm ────────────────────────────────────────

  @Nested
  class ConfirmEmail {

    @Test
    void returns200AndEnablesUser() throws Exception {
      testUser.setEnabled(false);
      appUserRepository.save(testUser);

      UUID token = UUID.randomUUID();
      emailConfirmationRepository.save(EmailConfirmationEntity.builder().tokenHash(TokenHashUtil.sha256Hex(token.toString())).user(testUser).expiry(LocalDateTime.now().plusHours(24)).build());

      mockMvc.perform(post("/api/auth/confirm").contentType(MediaType.APPLICATION_JSON).content(confirmJson(token))).andExpect(status().isOk());

      AppUserEntity updated = appUserRepository.findByUsername("testuser").orElseThrow();
      assertThat(updated.getEnabled()).isTrue();
      assertThat(emailConfirmationRepository.findByUser(updated)).isEmpty();
    }

    @Test
    void returns400ForNonExistentToken() throws Exception {
      mockMvc.perform(post("/api/auth/confirm").contentType(MediaType.APPLICATION_JSON).content(confirmJson(UUID.randomUUID()))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.messages[0].code").value("AU_400_03"));
    }

    @Test
    void returns400ForExpiredToken() throws Exception {
      testUser.setEnabled(false);
      appUserRepository.save(testUser);

      UUID token = UUID.randomUUID();
      emailConfirmationRepository.save(EmailConfirmationEntity.builder().tokenHash(TokenHashUtil.sha256Hex(token.toString())).user(testUser).expiry(LocalDateTime.now().minusHours(1)).build());

      mockMvc.perform(post("/api/auth/confirm").contentType(MediaType.APPLICATION_JSON).content(confirmJson(token))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.messages[0].code").value("AU_400_03"));

      AppUserEntity updated = appUserRepository.findByUsername("testuser").orElseThrow();
      assertThat(updated.getEnabled()).isFalse();
    }

    @Test
    void returns200IdempotentlyForAlreadyEnabledUser() throws Exception {
      UUID token = UUID.randomUUID();
      emailConfirmationRepository.save(EmailConfirmationEntity.builder().tokenHash(TokenHashUtil.sha256Hex(token.toString())).user(testUser).expiry(LocalDateTime.now().plusHours(24)).build());

      mockMvc.perform(post("/api/auth/confirm").contentType(MediaType.APPLICATION_JSON).content(confirmJson(token))).andExpect(status().isOk());
    }

    @Test
    void returns400ForMissingToken() throws Exception {
      mockMvc.perform(post("/api/auth/confirm").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }
  }

  // ── POST /api/auth/resend-confirmation ───────────────────────────

  @Nested
  class ResendConfirmation {

    private String resendJson(String username) {
      return """
          {"username":"%s"}""".formatted(username);
    }

    @Test
    void resend_sendsEmailForUnconfirmedUser() throws Exception {
      testUser.setEnabled(false);
      appUserRepository.save(testUser);
      emailConfirmationRepository.save(EmailConfirmationEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex("oldtoken"))
          .user(testUser)
          .expiry(LocalDateTime.now().plusHours(12))
          .build());

      mockMvc.perform(post("/api/auth/resend-confirmation")
              .contentType(MediaType.APPLICATION_JSON)
              .content(resendJson("testuser")))
          .andExpect(status().isOk());

      verify(mailClient).sendEmail(any(SendMailRequest.class));
    }

    @Test
    void resend_throttled_doesNotSendEmail() throws Exception {
      testUser.setEnabled(false);
      appUserRepository.save(testUser);
      emailConfirmationRepository.save(EmailConfirmationEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex("recent"))
          .user(testUser)
          .expiry(LocalDateTime.now().plusHours(24))
          .build());

      mockMvc.perform(post("/api/auth/resend-confirmation")
              .contentType(MediaType.APPLICATION_JSON)
              .content(resendJson("testuser")))
          .andExpect(status().isOk());

      verify(mailClient, never()).sendEmail(any(SendMailRequest.class));
    }

    @Test
    void resend_returnsOkForEnabledUser() throws Exception {
      mockMvc.perform(post("/api/auth/resend-confirmation")
              .contentType(MediaType.APPLICATION_JSON)
              .content(resendJson("testuser")))
          .andExpect(status().isOk());

      verify(mailClient, never()).sendEmail(any(SendMailRequest.class));
    }

    @Test
    void resend_returnsOkForNonExistentUser() throws Exception {
      mockMvc.perform(post("/api/auth/resend-confirmation")
              .contentType(MediaType.APPLICATION_JSON)
              .content(resendJson("unknownuser")))
          .andExpect(status().isOk());

      verify(mailClient, never()).sendEmail(any(SendMailRequest.class));
    }

    @Test
    void resend_regeneratesTokenHash() throws Exception {
      testUser.setEnabled(false);
      appUserRepository.save(testUser);
      String oldHash = TokenHashUtil.sha256Hex("oldtoken");
      emailConfirmationRepository.save(EmailConfirmationEntity.builder()
          .tokenHash(oldHash)
          .user(testUser)
          .expiry(LocalDateTime.now().plusHours(12))
          .build());

      mockMvc.perform(post("/api/auth/resend-confirmation")
              .contentType(MediaType.APPLICATION_JSON)
              .content(resendJson("testuser")))
          .andExpect(status().isOk());

      EmailConfirmationEntity ec = emailConfirmationRepository.findByUser(testUser).orElseThrow();
      assertThat(ec.getTokenHash()).isNotEqualTo(oldHash);
      assertThat(ec.getExpiry()).isAfter(LocalDateTime.now().plusHours(23));
    }

    @Test
    void resend_returns400ForBlankUsername() throws Exception {
      mockMvc.perform(post("/api/auth/resend-confirmation")
              .contentType(MediaType.APPLICATION_JSON)
              .content(resendJson("")))
          .andExpect(status().isBadRequest());
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
      MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("testuser", TEST_PASSWORD))).andReturn();
      String refreshToken = JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");

      // Create an expired token
      refreshTokenRepository.save(RefreshTokenEntity.builder().user(testUser).tokenHash(TokenHashUtil.sha256Hex("expired-token")).expiresAt(LocalDateTime.now().minusDays(1)).created(LocalDateTime.now().minusDays(2)).revoked(false).build());

      // Create a revoked token
      refreshTokenRepository.save(RefreshTokenEntity.builder().user(testUser).tokenHash(TokenHashUtil.sha256Hex("revoked-token")).expiresAt(LocalDateTime.now().plusDays(1)).created(LocalDateTime.now()).revoked(true).build());

      assertThat(refreshTokenRepository.findAll()).hasSize(3);

      tokenCleanupTask.cleanupExpiredTokens();

      // Only the valid (non-expired, non-revoked) token should remain
      assertThat(refreshTokenRepository.findAll()).hasSize(1);
      assertThat(refreshTokenRepository.findActiveByHashAndUsername(TokenHashUtil.sha256Hex(refreshToken), "testuser")).isPresent();
    }
  }

  // ── UnconfirmedUserCleanupTask ─────────────────────────────────────

  @Nested
  class UnconfirmedUserCleanup {

    @Autowired
    private UnconfirmedUserCleanupTask cleanupTask;

    @Test
    void deletesUnconfirmedUserWithExpiredToken() {
      AppUserEntity stale = appUserRepository.save(AppUserEntity.builder()
          .username("stale").email("stale@aigame.it")
          .passwordHash("hash").salt("salt")
          .enabled(false).created(LocalDateTime.now().minusDays(5))
          .build());
      emailConfirmationRepository.save(EmailConfirmationEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex("old"))
          .user(stale)
          .expiry(LocalDateTime.now().minusHours(49))
          .build());

      cleanupTask.cleanupUnconfirmedUsers();

      assertThat(appUserRepository.findByUsername("stale")).isEmpty();
      assertThat(emailConfirmationRepository.findByUser(stale)).isEmpty();
    }

    @Test
    void doesNotDeleteConfirmedUser() {
      // testUser is enabled=true — must survive cleanup
      cleanupTask.cleanupUnconfirmedUsers();

      assertThat(appUserRepository.findByUsername("testuser")).isPresent();
    }

    @Test
    void doesNotDeleteUnconfirmedUserWithinGracePeriod() {
      AppUserEntity recent = appUserRepository.save(AppUserEntity.builder()
          .username("recent").email("recent@aigame.it")
          .passwordHash("hash").salt("salt")
          .enabled(false).created(LocalDateTime.now())
          .build());
      emailConfirmationRepository.save(EmailConfirmationEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex("fresh"))
          .user(recent)
          .expiry(LocalDateTime.now().minusHours(1))
          .build());

      cleanupTask.cleanupUnconfirmedUsers();

      assertThat(appUserRepository.findByUsername("recent")).isPresent();
    }

    @Test
    void cascadeDeletesRefreshTokenAndEmailConfirmation() {
      AppUserEntity stale = appUserRepository.save(AppUserEntity.builder()
          .username("cascade").email("cascade@aigame.it")
          .passwordHash("hash").salt("salt")
          .enabled(false).created(LocalDateTime.now().minusDays(5))
          .build());
      emailConfirmationRepository.save(EmailConfirmationEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex("ec"))
          .user(stale)
          .expiry(LocalDateTime.now().minusHours(49))
          .build());
      refreshTokenRepository.save(RefreshTokenEntity.builder()
          .user(stale)
          .tokenHash(TokenHashUtil.sha256Hex("rt"))
          .expiresAt(LocalDateTime.now().plusDays(1))
          .created(LocalDateTime.now())
          .revoked(false)
          .build());

      long ecBefore = emailConfirmationRepository.count();
      long rtBefore = refreshTokenRepository.count();

      cleanupTask.cleanupUnconfirmedUsers();

      assertThat(appUserRepository.findByUsername("cascade")).isEmpty();
      assertThat(emailConfirmationRepository.count()).isEqualTo(ecBefore - 1);
      assertThat(refreshTokenRepository.count()).isEqualTo(rtBefore - 1);
    }

    @Test
    void deletesExpiredPasswordResetTokens() {
      passwordResetRepository.save(PasswordResetEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex("expired-reset"))
          .user(testUser)
          .expiry(LocalDateTime.now().minusHours(2))
          .build());

      assertThat(passwordResetRepository.findByUser(testUser)).isPresent();

      cleanupTask.cleanupUnconfirmedUsers();

      assertThat(passwordResetRepository.findByUser(testUser)).isEmpty();
    }
  }

  // ── POST /api/auth/forgot-password ─────────────────────────────────

  @Nested
  class ForgotPassword {

    @Test
    void sendsEmailForEnabledUser() throws Exception {
      mockMvc.perform(post("/api/auth/forgot-password")
              .contentType(MediaType.APPLICATION_JSON)
              .content(forgotPasswordJson("test@aigame.it")))
          .andExpect(status().isOk());

      verify(mailClient).sendEmail(any(SendMailRequest.class));
      assertThat(passwordResetRepository.findByUser(testUser)).isPresent();

      AppUserEntity updated = appUserRepository.findByUsername("testuser").orElseThrow();
      assertThat(updated.getLastEmailSentAt()).isNotNull();
    }

    @Test
    void returns200ForNonExistentEmail_noEmail() throws Exception {
      mockMvc.perform(post("/api/auth/forgot-password")
              .contentType(MediaType.APPLICATION_JSON)
              .content(forgotPasswordJson("unknown@aigame.it")))
          .andExpect(status().isOk());

      verify(mailClient, never()).sendEmail(any(SendMailRequest.class));
    }

    @Test
    void returns200ForDisabledUser_noEmail() throws Exception {
      testUser.setEnabled(false);
      appUserRepository.save(testUser);

      mockMvc.perform(post("/api/auth/forgot-password")
              .contentType(MediaType.APPLICATION_JSON)
              .content(forgotPasswordJson("test@aigame.it")))
          .andExpect(status().isOk());

      verify(mailClient, never()).sendEmail(any(SendMailRequest.class));
    }

    @Test
    void throttled_doesNotSendEmail() throws Exception {
      testUser.setLastEmailSentAt(LocalDateTime.now().minusMinutes(30));
      appUserRepository.save(testUser);

      mockMvc.perform(post("/api/auth/forgot-password")
              .contentType(MediaType.APPLICATION_JSON)
              .content(forgotPasswordJson("test@aigame.it")))
          .andExpect(status().isOk());

      verify(mailClient, never()).sendEmail(any(SendMailRequest.class));
    }

    @Test
    void returns400ForInvalidEmail() throws Exception {
      mockMvc.perform(post("/api/auth/forgot-password")
              .contentType(MediaType.APPLICATION_JSON)
              .content(forgotPasswordJson("not-an-email")))
          .andExpect(status().isBadRequest());
    }

    @Test
    void updatesExistingResetToken() throws Exception {
      String oldHash = TokenHashUtil.sha256Hex("old-reset");
      passwordResetRepository.save(PasswordResetEntity.builder()
          .tokenHash(oldHash)
          .user(testUser)
          .expiry(LocalDateTime.now().plusMinutes(30))
          .build());

      mockMvc.perform(post("/api/auth/forgot-password")
              .contentType(MediaType.APPLICATION_JSON)
              .content(forgotPasswordJson("test@aigame.it")))
          .andExpect(status().isOk());

      PasswordResetEntity updated = passwordResetRepository.findByUser(testUser).orElseThrow();
      assertThat(updated.getTokenHash()).isNotEqualTo(oldHash);
      assertThat(passwordResetRepository.findAll()).hasSize(1);
    }
  }

  // ── POST /api/auth/reset-password ──────────────────────────────────

  @Nested
  class ResetPassword {

    @Test
    void resetsPasswordAndRevokesRefreshTokens() throws Exception {
      // Login to create a refresh token
      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(loginJson("testuser", TEST_PASSWORD)))
          .andExpect(status().isOk());

      assertThat(refreshTokenRepository.findAll().stream()
          .filter(r -> !r.getRevoked()).count()).isGreaterThanOrEqualTo(1);

      // Create a valid reset token
      UUID plainToken = UUID.randomUUID();
      passwordResetRepository.save(PasswordResetEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex(plainToken.toString()))
          .user(testUser)
          .expiry(LocalDateTime.now().plusHours(1))
          .build());

      String newPassword = "NewPass999";
      mockMvc.perform(post("/api/auth/reset-password")
              .contentType(MediaType.APPLICATION_JSON)
              .content(resetPasswordJson(plainToken, newPassword)))
          .andExpect(status().isOk());

      // Reset token deleted
      assertThat(passwordResetRepository.findByUser(testUser)).isEmpty();

      // All refresh tokens revoked
      assertThat(refreshTokenRepository.findAll().stream()
          .filter(r -> r.getUser().getId().equals(testUser.getId()))
          .allMatch(RefreshTokenEntity::getRevoked)).isTrue();

      // Can login with new password
      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(loginJson("testuser", newPassword)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken", notNullValue()));

      // Old password no longer works
      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(loginJson("testuser", TEST_PASSWORD)))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void returns400ForNonExistentToken() throws Exception {
      mockMvc.perform(post("/api/auth/reset-password")
              .contentType(MediaType.APPLICATION_JSON)
              .content(resetPasswordJson(UUID.randomUUID(), "NewPass123")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messages[0].code").value("AU_400_04"));
    }

    @Test
    void returns400ForExpiredToken() throws Exception {
      UUID plainToken = UUID.randomUUID();
      passwordResetRepository.save(PasswordResetEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex(plainToken.toString()))
          .user(testUser)
          .expiry(LocalDateTime.now().minusMinutes(1))
          .build());

      mockMvc.perform(post("/api/auth/reset-password")
              .contentType(MediaType.APPLICATION_JSON)
              .content(resetPasswordJson(plainToken, "NewPass123")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messages[0].code").value("AU_400_04"));
    }

    @Test
    void returns400ForShortPassword() throws Exception {
      UUID plainToken = UUID.randomUUID();
      passwordResetRepository.save(PasswordResetEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex(plainToken.toString()))
          .user(testUser)
          .expiry(LocalDateTime.now().plusHours(1))
          .build());

      mockMvc.perform(post("/api/auth/reset-password")
              .contentType(MediaType.APPLICATION_JSON)
              .content(resetPasswordJson(plainToken, "short")))
          .andExpect(status().isBadRequest());
    }

    @Test
    void returns400ForMissingToken() throws Exception {
      mockMvc.perform(post("/api/auth/reset-password")
              .contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {"newPassword":"ValidPass1"}"""))
          .andExpect(status().isBadRequest());
    }
  }

  // ── POST /api/auth/google ───────────────────────────────────────────

  @Nested
  class GoogleAuth {

    private static final String GOOGLE_EMAIL = "google@aigame.it";
    private static final String GOOGLE_NAME = "John Doe";
    private static final String VALID_CREDENTIAL = "valid-google-credential";

    @Test
    void returns401ForInvalidGoogleToken() throws Exception {
      when(googleTokenVerifier.verify(anyString()))
          .thenThrow(new com.ondgard.game.exception.UnauthorizedException("AU_401_04", "Invalid Google token"));

      mockMvc.perform(post("/api/auth/google")
              .contentType(MediaType.APPLICATION_JSON)
              .content(googleAuthJson("invalid-token", null)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.messages[0].code").value("AU_401_04"));
    }

    @Test
    void newUser_withoutUsername_returns404WithRegistrationRequired() throws Exception {
      when(googleTokenVerifier.verify(VALID_CREDENTIAL))
          .thenReturn(new GoogleUserInfo(GOOGLE_EMAIL, GOOGLE_NAME));

      mockMvc.perform(post("/api/auth/google")
              .contentType(MediaType.APPLICATION_JSON)
              .content(googleAuthJson(VALID_CREDENTIAL, null)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.email").value(GOOGLE_EMAIL))
          .andExpect(jsonPath("$.suggestedUsername", notNullValue()));
    }

    @Test
    void newUser_withUsername_returns200WithTokens() throws Exception {
      when(googleTokenVerifier.verify(VALID_CREDENTIAL))
          .thenReturn(new GoogleUserInfo(GOOGLE_EMAIL, GOOGLE_NAME));

      mockMvc.perform(post("/api/auth/google")
              .contentType(MediaType.APPLICATION_JSON)
              .content(googleAuthJson(VALID_CREDENTIAL, "googlenew")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken", notNullValue()))
          .andExpect(jsonPath("$.refreshToken", notNullValue()))
          .andExpect(jsonPath("$.expiresIn").value(1800));

      AppUserEntity created = appUserRepository.findByUsername("googlenew").orElseThrow();
      assertThat(created.getEnabled()).isTrue();
      assertThat(created.getEmail()).isEqualTo(GOOGLE_EMAIL);
      assertThat(created.getPasswordHash()).isNotBlank();
      verify(chatClient).createUser(any(), eq("googlenew"));
    }

    @Test
    void existingEnabledUser_returns200WithTokens() throws Exception {
      when(googleTokenVerifier.verify(VALID_CREDENTIAL))
          .thenReturn(new GoogleUserInfo("test@aigame.it", GOOGLE_NAME));

      LocalDateTime before = LocalDateTime.now().minusSeconds(1);

      mockMvc.perform(post("/api/auth/google")
              .contentType(MediaType.APPLICATION_JSON)
              .content(googleAuthJson(VALID_CREDENTIAL, null)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken", notNullValue()))
          .andExpect(jsonPath("$.refreshToken", notNullValue()));

      AppUserEntity updated = appUserRepository.findByUsername("testuser").orElseThrow();
      assertThat(updated.getLastLogin()).isAfter(before);
    }

    @Test
    void existingDisabledUser_returns200AndEnables() throws Exception {
      testUser.setEnabled(false);
      appUserRepository.save(testUser);
      emailConfirmationRepository.save(EmailConfirmationEntity.builder()
          .tokenHash(TokenHashUtil.sha256Hex("pending"))
          .user(testUser)
          .expiry(LocalDateTime.now().plusHours(24))
          .build());

      when(googleTokenVerifier.verify(VALID_CREDENTIAL))
          .thenReturn(new GoogleUserInfo("test@aigame.it", GOOGLE_NAME));

      mockMvc.perform(post("/api/auth/google")
              .contentType(MediaType.APPLICATION_JSON)
              .content(googleAuthJson(VALID_CREDENTIAL, null)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken", notNullValue()));

      AppUserEntity updated = appUserRepository.findByUsername("testuser").orElseThrow();
      assertThat(updated.getEnabled()).isTrue();
      assertThat(emailConfirmationRepository.findByUser(updated)).isEmpty();
      verify(chatClient).createUser(any(), eq("testuser"));
    }

    @Test
    void newUser_usernameTaken_returns400() throws Exception {
      when(googleTokenVerifier.verify(VALID_CREDENTIAL))
          .thenReturn(new GoogleUserInfo(GOOGLE_EMAIL, GOOGLE_NAME));

      mockMvc.perform(post("/api/auth/google")
              .contentType(MediaType.APPLICATION_JSON)
              .content(googleAuthJson(VALID_CREDENTIAL, "testuser")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messages[0].code").value("AU_400_01"));
    }

    @Test
    void newUser_withoutUsername_suggestsAvailableUsernameWithSuffix() throws Exception {
      // "testuser" is already taken (created in setUp), Google name = "testuser"
      when(googleTokenVerifier.verify(VALID_CREDENTIAL))
          .thenReturn(new GoogleUserInfo(GOOGLE_EMAIL, "testuser"));

      mockMvc.perform(post("/api/auth/google")
              .contentType(MediaType.APPLICATION_JSON)
              .content(googleAuthJson(VALID_CREDENTIAL, null)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.email").value(GOOGLE_EMAIL))
          .andExpect(jsonPath("$.suggestedUsername").value("testuser1"));
    }

    @Test
    void newUser_withoutUsername_skipsMultipleTakenSuffixes() throws Exception {
      // "mario" is taken
      appUserRepository.save(AppUserEntity.builder()
          .username("mario").email("mario@aigame.it")
          .passwordHash("hash").salt("salt")
          .enabled(true).created(LocalDateTime.now())
          .build());
      // "mario1" is also taken
      appUserRepository.save(AppUserEntity.builder()
          .username("mario1").email("mario1@aigame.it")
          .passwordHash("hash").salt("salt")
          .enabled(true).created(LocalDateTime.now())
          .build());

      when(googleTokenVerifier.verify(VALID_CREDENTIAL))
          .thenReturn(new GoogleUserInfo(GOOGLE_EMAIL, "mario"));

      mockMvc.perform(post("/api/auth/google")
              .contentType(MediaType.APPLICATION_JSON)
              .content(googleAuthJson(VALID_CREDENTIAL, null)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.suggestedUsername").value("mario2"));
    }

    @Test
    void lockedUser_returns401() throws Exception {
      testUser.setLockedUntil(LocalDateTime.now().plusHours(1));
      appUserRepository.save(testUser);

      when(googleTokenVerifier.verify(VALID_CREDENTIAL))
          .thenReturn(new GoogleUserInfo("test@aigame.it", GOOGLE_NAME));

      mockMvc.perform(post("/api/auth/google")
              .contentType(MediaType.APPLICATION_JSON)
              .content(googleAuthJson(VALID_CREDENTIAL, null)))
          .andExpect(status().isUnauthorized());
    }
  }
}
