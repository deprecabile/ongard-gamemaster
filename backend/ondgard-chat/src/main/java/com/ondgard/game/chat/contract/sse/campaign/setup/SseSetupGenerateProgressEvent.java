package com.ondgard.game.chat.contract.sse.campaign.setup;

import java.time.LocalDateTime;

public record SseSetupGenerateProgressEvent( LocalDateTime tms, String code ) {
}
