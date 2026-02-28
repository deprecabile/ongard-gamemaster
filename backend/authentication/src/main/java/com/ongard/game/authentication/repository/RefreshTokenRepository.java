package com.ongard.game.authentication.repository;

import com.ongard.game.authentication.entity.RefreshTokenEntity;
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

  Optional<RefreshTokenEntity> findByTokenAndRevokedFalseAndUser_Username(String token, String username);

  @Modifying
  @Transactional
  @Query( "DELETE FROM RefreshTokenEntity r WHERE r.expiresAt < :now OR r.revoked = true" )
  int deleteExpiredOrRevoked(@Param( "now" ) LocalDateTime now);
}
