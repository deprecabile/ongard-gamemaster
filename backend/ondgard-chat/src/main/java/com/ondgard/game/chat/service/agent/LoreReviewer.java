package com.ondgard.game.chat.service.agent;

import com.ondgard.game.chat.model.agent.ReviewerResponse;

public interface LoreReviewer {

  ReviewerResponse review(String draft);

  String topic();
}
