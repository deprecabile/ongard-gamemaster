package com.ondgard.game.chat.contract.sse.campaign.ask;

import java.time.LocalDateTime;

public record SseAdvisorCompletedEvent( LocalDateTime tms, String advisorOutput ) {
}
