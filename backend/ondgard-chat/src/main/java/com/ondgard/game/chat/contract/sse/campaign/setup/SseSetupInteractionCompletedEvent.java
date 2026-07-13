package com.ondgard.game.chat.contract.sse.campaign.setup;

import java.time.LocalDateTime;

public record SseSetupInteractionCompletedEvent( LocalDateTime tms, String advisorOutput ) {
}
