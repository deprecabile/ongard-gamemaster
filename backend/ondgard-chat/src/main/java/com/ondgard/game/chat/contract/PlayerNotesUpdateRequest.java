package com.ondgard.game.chat.contract;

public record PlayerNotesUpdateRequest( String characterHash, String newNotesSnapshot ) {
}
