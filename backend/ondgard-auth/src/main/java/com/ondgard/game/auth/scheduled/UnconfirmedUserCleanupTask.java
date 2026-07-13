package com.ondgard.game.auth.scheduled;

import com.ondgard.game.auth.repository.AppUserRepository;
import com.ondgard.game.auth.repository.PasswordResetRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class UnconfirmedUserCleanupTask {

  private static final long LOCK_ID = 1001L;

  private final AppUserRepository appUserRepository;
  private final PasswordResetRepository passwordResetRepository;
  private final EntityManager entityManager;

  @Scheduled( cron = "0 0 */6 * * *" )
  @Transactional
  public void cleanupUnconfirmedUsers() {
    Boolean acquired = (Boolean) entityManager
        .createNativeQuery("SELECT pg_try_advisory_xact_lock(:lockId)", Boolean.class)
        .setParameter("lockId", LOCK_ID)
        .getSingleResult();

    if( !acquired ){
      log.debug("Unconfirmed user cleanup: skipped, another instance holds lock {}", LOCK_ID);
      return;
    }

    LocalDateTime now = LocalDateTime.now();

    int deletedUsers = appUserRepository.deleteUnconfirmedExpired(now.minusHours(48));
    log.info("Unconfirmed user cleanup: deleted {} users", deletedUsers);

    int deletedResets = passwordResetRepository.deleteExpired(now);
    log.info("Password reset cleanup: deleted {} expired tokens", deletedResets);
  }
}
