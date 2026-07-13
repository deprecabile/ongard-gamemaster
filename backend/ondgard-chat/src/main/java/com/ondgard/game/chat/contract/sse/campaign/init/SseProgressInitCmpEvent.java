package com.ondgard.game.chat.contract.sse.campaign.init;

import java.time.LocalDateTime;

public record SseProgressInitCmpEvent( LocalDateTime tms, String code ) {
}
