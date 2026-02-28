package com.ongard.game.authentication.scheduled;

import com.ongard.game.authentication.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupTask {

  private final RefreshTokenRepository refreshTokenRepository;

  @Scheduled( cron = "0 0 3 * * *" )
  public void cleanupExpiredTokens() {
    int deleted = refreshTokenRepository.deleteExpiredOrRevoked(LocalDateTime.now());
    log.info("Refresh token cleanup: deleted {} expired/revoked tokens", deleted);
  }
}
