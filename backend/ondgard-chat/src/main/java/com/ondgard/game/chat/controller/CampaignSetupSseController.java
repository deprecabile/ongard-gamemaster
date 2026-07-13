package com.ondgard.game.chat.controller;

import com.ondgard.game.chat.contract.campaign.setup.SetupGenerateRequest;
import com.ondgard.game.chat.contract.campaign.setup.SetupInteractionRequest;
import com.ondgard.game.chat.service.campaign.setup.CampaignSetupGenerateService;
import com.ondgard.game.chat.service.campaign.setup.CampaignSetupInteractionService;
import com.ondgard.game.header.GameUserHeader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping( "/api/sse/campaign/setup" )
@RequiredArgsConstructor
public class CampaignSetupSseController {

  private final CampaignSetupGenerateService generateService;
  private final CampaignSetupInteractionService interactionService;

  @PostMapping( value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE )
  public SseEmitter generate(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestBody SetupGenerateRequest request) {
    return generateService.generate(userHeader, request);
  }

  @PostMapping( value = "/interaction", produces = MediaType.TEXT_EVENT_STREAM_VALUE )
  public SseEmitter interaction(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestBody SetupInteractionRequest request) {
    return interactionService.interact(userHeader, request);
  }
}
