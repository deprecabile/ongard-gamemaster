package com.ondgard.game.chat.contract.sse.campaign.init;

import java.time.LocalDateTime;

public record SseCompletedInitCmpEvent( LocalDateTime tms, boolean ok ) {
}
