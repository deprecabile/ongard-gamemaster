package com.ondgard.game.account.controller;

import com.ondgard.game.account.contract.LimitsResponse;
import com.ondgard.game.account.contract.TokenUsageOverviewResponse;
import com.ondgard.game.account.contract.UpdateLimitsRequest;
import com.ondgard.game.account.model.dto.TokenUsageOverviewDto;
import com.ondgard.game.account.service.TokenUsageService;
import com.ondgard.game.header.GameUserHeader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping( "/api/token-usage" )
@RequiredArgsConstructor
public class TokenUsageController {

  private final TokenUsageService tokenUsageService;

  @GetMapping
  public ResponseEntity<TokenUsageOverviewResponse> getUsageOverview(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader) {
    var overview = tokenUsageService.getUsageOverview(userHeader.getUserId());
    return ResponseEntity.ok(toResponse(overview));
  }

  @PutMapping( "/limits" )
  public ResponseEntity<Void> updateLimits(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestBody UpdateLimitsRequest request) {
    tokenUsageService.updateLimitsExternal(userHeader.getUserId(), request.limitMonth(), request.limitTotal());
    return ResponseEntity.ok().build();
  }

  private TokenUsageOverviewResponse toResponse(TokenUsageOverviewDto dto) {
    LimitsResponse limits = dto.limit() != null
        ? new LimitsResponse(dto.limit().getLimitMonth(), dto.limit().getLimitTotal(), dto.limit().getVersion())
        : null;
    return new TokenUsageOverviewResponse(limits, dto.usage(), dto.characters());
  }
}
