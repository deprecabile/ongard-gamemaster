package com.ondgard.game.auth.service;

import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.ondgard.game.auth.contract.LoginResponse;
import com.ondgard.game.auth.entity.AppUserEntity;
import com.ondgard.game.auth.entity.RefreshTokenEntity;
import com.ondgard.game.auth.model.validation.AuthApiErrorCode;
import com.ondgard.game.auth.repository.RefreshTokenRepository;
import com.ondgard.game.auth.util.TokenHashUtil;
import com.ondgard.game.exception.UnauthorizedException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

  @Value( "${jwt.secret}" )
  private String secret;

  @Getter
  @Value( "${jwt.access-token-expiration}" )
  private long accessTokenExpiration;

  @Value( "${jwt.refresh-token-expiration}" )
  private long refreshTokenExpiration;

  private final RefreshTokenRepository refreshTokenRepository;

  public String generateAccessToken(AppUserEntity user) {
    JwtEncoder encoder = createEncoder();

    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder().subject(user.getUserHash().toString()).claim("username", user.getUsername()).issuedAt(now).expiresAt(now.plusSeconds(accessTokenExpiration)).build();

    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public String generateRefreshToken(AppUserEntity user) {
    String plainToken = UUID.randomUUID().toString();
    String tokenHash = TokenHashUtil.sha256Hex(plainToken);

    RefreshTokenEntity refreshToken = RefreshTokenEntity.builder().user(user).tokenHash(tokenHash).expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration)).created(LocalDateTime.now()).revoked(false).build();

    refreshTokenRepository.save(refreshToken);
    return plainToken;
  }

  public LoginResponse rotateRefreshToken(String plainToken, String username) {
    String tokenHash = TokenHashUtil.sha256Hex(plainToken);
    RefreshTokenEntity existing = refreshTokenRepository.findActiveByHashAndUsername(tokenHash, username).orElseThrow(() -> new UnauthorizedException(AuthApiErrorCode.INVALID_REFRESH_TOKEN, "Invalid refresh token"));

    if( !existing.getExpiresAt().isAfter(LocalDateTime.now()) ){
      existing.setRevoked(true);
      refreshTokenRepository.save(existing);
      throw new UnauthorizedException(AuthApiErrorCode.EXPIRED_REFRESH_TOKEN, "Refresh token expired");
    }

    existing.setRevoked(true);
    refreshTokenRepository.save(existing);

    AppUserEntity user = existing.getUser();
    String newAccessToken = generateAccessToken(user);
    String newRefreshToken = generateRefreshToken(user);

    return LoginResponse.builder().accessToken(newAccessToken).refreshToken(newRefreshToken).expiresIn(accessTokenExpiration).build();
  }

  private JwtEncoder createEncoder() {
    byte[] keyBytes = Base64.getDecoder().decode(secret);
    SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretKey).build();
    return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(jwk.toSecretKey()));
  }
}
