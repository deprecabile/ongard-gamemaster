package com.ondgard.game.chat.repository;

import com.ondgard.game.chat.entity.ChatUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatUserRepository extends JpaRepository<ChatUserEntity, Long> {
  Optional<ChatUserEntity> findByUserHash(UUID userHash);
}
