package com.ondgard.game.chat.contract;

public record ChatInteractionRequest( String characterHash, ChatMode mode, String message ) {
}
