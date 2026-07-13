package com.ondgard.game.chat.contract.sse;

import java.time.LocalDateTime;

public record SseErrorEvent( LocalDateTime tms, String errorCode, String description ) {
}
