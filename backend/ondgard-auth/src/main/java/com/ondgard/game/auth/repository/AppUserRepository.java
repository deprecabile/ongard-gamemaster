package com.ondgard.game.auth.repository;

import com.ondgard.game.auth.entity.AppUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  Optional<AppUserEntity> findByUsername(String username);

  Optional<AppUserEntity> findByEmail(String email);

  @Query( """
      SELECT COUNT(u) = 0 FROM AppUserEntity u
      WHERE u.username = :username
      AND (u.enabled = true
           OR EXISTS (SELECT 1 FROM EmailConfirmationEntity ec
                      WHERE ec.user = u AND ec.expiry >= CURRENT_TIMESTAMP))
      """ )
  boolean isUsernameAvailable(@Param( "username" ) String username);

  @Modifying
  @Query( """
      DELETE FROM AppUserEntity u
      WHERE u.enabled = false
      AND EXISTS (SELECT 1 FROM EmailConfirmationEntity ec
                  WHERE ec.user = u AND ec.expiry < :cutoff)
      """ )
  int deleteUnconfirmedExpired(@Param( "cutoff" ) LocalDateTime cutoff);
}
