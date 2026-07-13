package com.ondgard.game.auth.repository;

import com.ondgard.game.auth.entity.AppUserEntity;
import com.ondgard.game.auth.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

  @Query( "SELECT r FROM RefreshTokenEntity r WHERE r.tokenHash = :tokenHash AND r.revoked = false AND r.user.username = :username" )
  Optional<RefreshTokenEntity> findActiveByHashAndUsername(@Param( "tokenHash" ) String tokenHash, @Param( "username" ) String username);

  @Modifying
  @Transactional
  @Query( "DELETE FROM RefreshTokenEntity r WHERE r.expiresAt < :now OR r.revoked = true" )
  int deleteExpiredOrRevoked(@Param( "now" ) LocalDateTime now);

  @Modifying
  @Transactional
  @Query( "UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.user = :user AND r.revoked = false" )
  int revokeByUser(@Param( "user" ) AppUserEntity user);
}
