package com.ondgard.game.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.ondgard.game.auth.model.GoogleUserInfo;
import com.ondgard.game.auth.model.validation.AuthApiErrorCode;
import com.ondgard.game.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class GoogleTokenVerifier {

  private final String clientId;
  private volatile GoogleIdTokenVerifier verifier;

  @Autowired
  public GoogleTokenVerifier(@Value( "${google.client.id}" ) String clientId) {
    this.clientId = clientId;
  }

  GoogleTokenVerifier(GoogleIdTokenVerifier verifier) {
    this.clientId = "test-only";
    this.verifier = verifier;
  }

  public GoogleUserInfo verify(String credential) {
    final GoogleIdToken idToken;
    try{
      idToken = getVerifier().verify(credential);
    }catch(Exception e){
      log.warn("Google token verification failed");
      throw new UnauthorizedException(AuthApiErrorCode.INVALID_GOOGLE_TOKEN, "Invalid Google token");
    }

    if( idToken == null ){
      log.warn("Google token verification failed");
      throw new UnauthorizedException(AuthApiErrorCode.INVALID_GOOGLE_TOKEN, "Invalid Google token");
    }

    GoogleIdToken.Payload payload = idToken.getPayload();

    if( !Boolean.TRUE.equals(payload.getEmailVerified()) ){
      log.warn("Google token email not verified");
      throw new UnauthorizedException(AuthApiErrorCode.INVALID_GOOGLE_TOKEN, "Invalid Google token");
    }

    String email = payload.getEmail();
    String name = (String) payload.get("name");

    return new GoogleUserInfo(email, name);
  }

  private GoogleIdTokenVerifier getVerifier() {
    if( verifier == null ){
      synchronized( this ){
        if( verifier == null ){
          try{
            verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
            ).setAudience(List.of(clientId)).build();
          }catch(Exception e){
            throw new IllegalStateException("Failed to initialize Google token verifier", e);
          }
        }
      }
    }
    return verifier;
  }
}
