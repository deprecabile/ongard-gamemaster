package com.ondgard.game.chat.contract.sse.campaign.ask;

import java.time.LocalDateTime;

public record SseAdvisorErrorEvent( LocalDateTime tms, String errorCode, String description ) {
}
