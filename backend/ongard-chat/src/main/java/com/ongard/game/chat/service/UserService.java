package com.ongard.game.chat.service;

import com.ongard.game.chat.contract.UserCreateRequest;
import com.ongard.game.chat.entity.ChatUserEntity;
import com.ongard.game.chat.repository.ChatUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final ChatUserRepository chatUserRepository;

    public void createUser(UserCreateRequest request) {
        ChatUserEntity user = ChatUserEntity.builder()
                .userHash(request.getUserHash())
                .username(request.getUsername())
                .build();
        chatUserRepository.save(user);
    }
}
