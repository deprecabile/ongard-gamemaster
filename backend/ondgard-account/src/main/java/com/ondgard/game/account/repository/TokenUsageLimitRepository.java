package com.ondgard.game.account.repository;

import com.ondgard.game.account.entity.TokenUsageLimitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenUsageLimitRepository extends JpaRepository<TokenUsageLimitEntity, Long> {

  Optional<TokenUsageLimitEntity> findTopByUserHashOrderByVersionDesc(String userHash);
}
