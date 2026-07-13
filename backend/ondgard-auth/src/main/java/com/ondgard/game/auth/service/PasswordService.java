package com.ondgard.game.auth.service;

import com.ondgard.game.auth.util.PasswordConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class PasswordService {

  private final Argon2PasswordEncoder passwordEncoder;

  public SaltedHash hash(String plainPassword) {
    byte[] saltBytes = new byte[PasswordConstants.SALT_LENGTH];
    new SecureRandom().nextBytes(saltBytes);
    String salt = Base64.getEncoder().encodeToString(saltBytes);
    String passwordHash = passwordEncoder.encode(PasswordConstants.PEPPER + plainPassword + salt);
    return new SaltedHash(salt, passwordHash);
  }

  public SaltedHash hashRandom() {
    byte[] randomBytes = new byte[PasswordConstants.SALT_LENGTH];
    new SecureRandom().nextBytes(randomBytes);
    String randomPassword = Base64.getEncoder().encodeToString(randomBytes);
    return hash(randomPassword);
  }

  public boolean matches(String plain, String salt, String hash) {
    return passwordEncoder.matches(PasswordConstants.PEPPER + plain + salt, hash);
  }

  public record SaltedHash( String salt, String passwordHash ) {
  }
}
