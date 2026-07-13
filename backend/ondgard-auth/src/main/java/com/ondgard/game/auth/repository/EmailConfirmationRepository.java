package com.ondgard.game.auth.repository;

import com.ondgard.game.auth.entity.AppUserEntity;
import com.ondgard.game.auth.entity.EmailConfirmationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailConfirmationRepository extends JpaRepository<EmailConfirmationEntity, Long> {

  Optional<EmailConfirmationEntity> findByTokenHash(String tokenHash);

  Optional<EmailConfirmationEntity> findByUser(AppUserEntity user);

  void deleteByUser(AppUserEntity user);
}
