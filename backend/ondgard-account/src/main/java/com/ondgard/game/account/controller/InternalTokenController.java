package com.ondgard.game.account.controller;

import com.ondgard.game.account.service.TokenUsageService;
import com.ondgard.game.contract.account.AddTokensRequest;
import com.ondgard.game.contract.account.CampaignInitTokenRequest;
import com.ondgard.game.contract.account.CheckLimitResponse;
import com.ondgard.game.contract.account.UserHashRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping( "/api/internal" )
@RequiredArgsConstructor
public class InternalTokenController {

  private final TokenUsageService tokenUsageService;

  @PostMapping( "/user/init" )
  public ResponseEntity<Void> initUserLimits(@RequestBody UserHashRequest request) {
    tokenUsageService.initUserLimits(request.userHash());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PostMapping( "/campaign/init" )
  public ResponseEntity<Void> initCampaignUsage(@RequestBody CampaignInitTokenRequest request) {
    tokenUsageService.initCampaignUsage(request.characterHash(), request.userHash());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PostMapping( "/tokens" )
  public ResponseEntity<Void> addTokens(@RequestBody AddTokensRequest request) {
    tokenUsageService.addTokens(request.characterHash(), request.tokens());
    return ResponseEntity.ok().build();
  }

  @GetMapping( "/check-limit" )
  public ResponseEntity<CheckLimitResponse> checkLimit(@RequestParam String userHash) {
    var result = tokenUsageService.checkLimit(userHash);
    return ResponseEntity.ok(new CheckLimitResponse(
        result.isPresent(),
        result.map(Enum::name).orElse(null)
    ));
  }
}
