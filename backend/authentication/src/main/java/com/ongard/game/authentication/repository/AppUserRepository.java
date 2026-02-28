package com.ongard.game.authentication.repository;

import com.ongard.game.authentication.entity.AppUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  Optional<AppUserEntity> findByUsername(String username);
}
