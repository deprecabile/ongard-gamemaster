package com.ondgard.game.auth.repository;

import com.ondgard.game.auth.entity.AppUserEntity;
import com.ondgard.game.auth.entity.PasswordResetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetRepository extends JpaRepository<PasswordResetEntity, Long> {

  Optional<PasswordResetEntity> findByTokenHash(String tokenHash);

  Optional<PasswordResetEntity> findByUser(AppUserEntity user);

  void deleteByUser(AppUserEntity user);

  @Modifying
  @Transactional
  @Query( "DELETE FROM PasswordResetEntity p WHERE p.expiry < :cutoff" )
  int deleteExpired(@Param( "cutoff" ) LocalDateTime cutoff);
}
