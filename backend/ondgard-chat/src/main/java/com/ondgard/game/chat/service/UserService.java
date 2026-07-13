package com.ondgard.game.chat.service;

import com.ondgard.game.chat.client.AccountClient;
import com.ondgard.game.chat.contract.UserCreateRequest;
import com.ondgard.game.chat.entity.ChatUserEntity;
import com.ondgard.game.chat.repository.ChatUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

  private final ChatUserRepository chatUserRepository;
  private final AccountClient accountClient;

  public void createUser(UserCreateRequest request) {
    accountClient.initUserLimits(request.getUserHash().toString());

    ChatUserEntity user = ChatUserEntity.builder()
        .userHash(request.getUserHash())
        .username(request.getUsername())
        .build();
    chatUserRepository.save(user);
  }
}
