package com.ondgard.game.chat.controller;

import com.ondgard.game.chat.contract.CampaignInitRequest;
import com.ondgard.game.chat.contract.ChatInteractionRequest;
import com.ondgard.game.chat.service.CampaignInitService;
import com.ondgard.game.chat.service.ChatInteractionService;
import com.ondgard.game.header.GameUserHeader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping( "/api/sse" )
@RequiredArgsConstructor
public class CampaignSseController {

  private final ChatInteractionService chatInteractionService;
  private final CampaignInitService campaignInitService;

  @PostMapping( value = "/interaction", produces = MediaType.TEXT_EVENT_STREAM_VALUE )
  public SseEmitter interaction(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestBody ChatInteractionRequest request) {
    return chatInteractionService.interact(userHeader, request);
  }

  @PostMapping( value = "/campaign/init", produces = MediaType.TEXT_EVENT_STREAM_VALUE )
  public SseEmitter initCampaign(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestBody CampaignInitRequest request) {
    return campaignInitService.initCampaign(userHeader, request);
  }
}
