package com.ongard.game.authentication.service;

import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.ongard.game.authentication.contract.LoginResponse;
import com.ongard.game.authentication.entity.AppUserEntity;
import com.ongard.game.authentication.entity.RefreshTokenEntity;
import com.ongard.game.authentication.model.validation.AuthApiErrorCode;
import com.ongard.game.authentication.repository.RefreshTokenRepository;
import com.ongard.game.exception.UnauthorizedException;
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
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .subject(user.getUserHash().toString())
        .claim("username", user.getUsername())
        .issuedAt(now)
        .expiresAt(now.plusSeconds(accessTokenExpiration))
        .build();

    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public String generateRefreshToken(AppUserEntity user) {
    String token = UUID.randomUUID().toString();

    RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
        .user(user)
        .token(token)
        .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration))
        .created(LocalDateTime.now())
        .revoked(false)
        .build();

    refreshTokenRepository.save(refreshToken);
    return token;
  }

  public LoginResponse rotateRefreshToken(String token, String username) {
    RefreshTokenEntity existing = refreshTokenRepository
        .findByTokenAndRevokedFalseAndUser_Username(token, username)
        .orElseThrow(() -> new UnauthorizedException(AuthApiErrorCode.INVALID_REFRESH_TOKEN, "Invalid refresh token"));

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

    return LoginResponse.builder()
        .accessToken(newAccessToken)
        .refreshToken(newRefreshToken)
        .expiresIn(accessTokenExpiration)
        .build();
  }

  private JwtEncoder createEncoder() {
    byte[] keyBytes = Base64.getDecoder().decode(secret);
    SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretKey).build();
    return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(jwk.toSecretKey()));
  }
}
