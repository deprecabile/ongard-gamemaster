package com.ondgard.game.account.contract;

import com.ondgard.game.account.model.dto.CharacterTokenUsageDto;
import com.ondgard.game.account.model.dto.PlayerTokenUsageDto;

import java.util.List;

public record TokenUsageOverviewResponse(
    LimitsResponse limits,
    PlayerTokenUsageDto usage,
    List<CharacterTokenUsageDto> characters
) {
}
