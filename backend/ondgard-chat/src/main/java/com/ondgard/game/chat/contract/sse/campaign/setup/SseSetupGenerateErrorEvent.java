package com.ondgard.game.chat.contract.sse.campaign.setup;

import java.time.LocalDateTime;

public record SseSetupGenerateErrorEvent( LocalDateTime tms, String errorCode, String description ) {
}
