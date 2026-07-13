package com.ondgard.game.chat.model.agent;

import java.util.Collection;

public record LoreValidationResult(
    boolean allPassed,
    Collection<ReviewerResponse> failures,
    String aggregatedFeedback
) {
}
