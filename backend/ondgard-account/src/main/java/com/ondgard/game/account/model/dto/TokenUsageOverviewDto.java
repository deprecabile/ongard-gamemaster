package com.ondgard.game.account.model.dto;

import com.ondgard.game.account.entity.TokenUsageLimitEntity;

import java.util.List;

public record TokenUsageOverviewDto(
    TokenUsageLimitEntity limit,
    PlayerTokenUsageDto usage,
    List<CharacterTokenUsageDto> characters
) {
}
