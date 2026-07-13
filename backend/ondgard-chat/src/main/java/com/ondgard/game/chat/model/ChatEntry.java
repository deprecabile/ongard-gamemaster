package com.ondgard.game.chat.model;

import java.io.Serializable;

public record ChatEntry(
    int turnNumber,
    String userMessage,
    String gmResponse
) implements Serializable {
}
