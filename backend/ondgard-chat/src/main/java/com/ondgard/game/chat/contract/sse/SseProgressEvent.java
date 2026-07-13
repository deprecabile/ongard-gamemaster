package com.ondgard.game.chat.contract.sse;

import java.time.LocalDateTime;

public record SseProgressEvent( LocalDateTime tms, String code ) {
}
