package com.ongard.game.chat.contract;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequest implements Serializable {

    private UUID userHash;
    private String username;
}
