package com.ondgard.game.chat.contract.sse;

import java.time.LocalDateTime;

public record SseCompletedEvent( LocalDateTime tms, String gmOutput, boolean inconsistencyDetected ) {
}
