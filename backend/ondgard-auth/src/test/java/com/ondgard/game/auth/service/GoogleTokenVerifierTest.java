package com.ondgard.game.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.ondgard.game.auth.model.GoogleUserInfo;
import com.ondgard.game.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleTokenVerifierTest {

  private GoogleIdTokenVerifier idTokenVerifier;
  private GoogleTokenVerifier googleTokenVerifier;

  @BeforeEach
  void setUp() {
    idTokenVerifier = mock(GoogleIdTokenVerifier.class);
    googleTokenVerifier = new GoogleTokenVerifier(idTokenVerifier);
  }

  @Test
  void verify_nullToken_throwsUnauthorized() throws GeneralSecurityException, IOException {
    when(idTokenVerifier.verify("invalid-token")).thenReturn(null);

    assertThatThrownBy(() -> googleTokenVerifier.verify("invalid-token"))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void verify_securityException_throwsUnauthorized() throws GeneralSecurityException, IOException {
    when(idTokenVerifier.verify("bad-token")).thenThrow(new GeneralSecurityException("fail"));

    assertThatThrownBy(() -> googleTokenVerifier.verify("bad-token"))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void verify_emailNotVerified_throwsUnauthorized() throws GeneralSecurityException, IOException {
    GoogleIdToken idToken = mock(GoogleIdToken.class);
    GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
    when(idTokenVerifier.verify("token")).thenReturn(idToken);
    when(idToken.getPayload()).thenReturn(payload);
    when(payload.getEmailVerified()).thenReturn(false);

    assertThatThrownBy(() -> googleTokenVerifier.verify("token"))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void verify_validToken_returnsGoogleUserInfo() throws GeneralSecurityException, IOException {
    GoogleIdToken idToken = mock(GoogleIdToken.class);
    GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
    when(idTokenVerifier.verify("valid-token")).thenReturn(idToken);
    when(idToken.getPayload()).thenReturn(payload);
    when(payload.getEmailVerified()).thenReturn(true);
    when(payload.getEmail()).thenReturn("user@example.com");
    when(payload.get("name")).thenReturn("John Doe");

    GoogleUserInfo result = googleTokenVerifier.verify("valid-token");

    assertThat(result.email()).isEqualTo("user@example.com");
    assertThat(result.name()).isEqualTo("John Doe");
  }

  @Test
  void verify_validTokenWithNullName_returnsGoogleUserInfoWithNullName() throws GeneralSecurityException, IOException {
    GoogleIdToken idToken = mock(GoogleIdToken.class);
    GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
    when(idTokenVerifier.verify("valid-token")).thenReturn(idToken);
    when(idToken.getPayload()).thenReturn(payload);
    when(payload.getEmailVerified()).thenReturn(true);
    when(payload.getEmail()).thenReturn("user@example.com");
    when(payload.get("name")).thenReturn(null);

    GoogleUserInfo result = googleTokenVerifier.verify("valid-token");

    assertThat(result.email()).isEqualTo("user@example.com");
    assertThat(result.name()).isNull();
  }
}
