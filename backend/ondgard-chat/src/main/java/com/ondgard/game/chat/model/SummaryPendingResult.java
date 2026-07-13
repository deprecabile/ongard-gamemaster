package com.ondgard.game.chat.model;

import java.io.Serializable;

public record SummaryPendingResult(
    String narrativeSummary,
    int summaryVersion
) implements Serializable {
}
