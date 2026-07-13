package com.ondgard.game.chat.contract.sse.campaign.setup;

import java.time.LocalDateTime;

public record SseSetupGenerateCompletedEvent( LocalDateTime tms, String characterPrompt, String startingSituation,
                                              String raceCode, String characterName ) {
}
