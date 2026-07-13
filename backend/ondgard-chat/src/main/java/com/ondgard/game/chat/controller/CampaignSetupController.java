package com.ondgard.game.chat.controller;

import com.ondgard.game.chat.contract.campaign.setup.CampaignArchetypeResponse;
import com.ondgard.game.chat.contract.campaign.setup.SetupSessionFieldRequest;
import com.ondgard.game.chat.contract.campaign.setup.SetupSessionFormResponse;
import com.ondgard.game.chat.contract.campaign.setup.SetupSessionStatusResponse;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.setup.CampaignSetupSession;
import com.ondgard.game.chat.service.campaign.setup.ArchetypeService;
import com.ondgard.game.chat.service.campaign.setup.CampaignSetupSessionService;
import com.ondgard.game.header.GameUserHeader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping( "/api/campaign/setup" )
@RequiredArgsConstructor
public class CampaignSetupController {

  private final ArchetypeService archetypeService;
  private final CampaignSetupSessionService sessionService;

  @GetMapping( "/archetypes" )
  public Collection<CampaignArchetypeResponse> getArchetypes(@RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader) {
    return archetypeService.getRandomArchetypes(userHeader.getLanguage(), 3);
  }

  @GetMapping( "/session/status" )
  public SetupSessionStatusResponse getSessionStatus(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader) {
    return new SetupSessionStatusResponse(sessionService.exists(userHeader.getUserId()));
  }

  @GetMapping( "/session" )
  public ResponseEntity<SetupSessionFormResponse> getSession(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader) {
    return sessionService.get(userHeader.getUserId())
        .map(s -> ResponseEntity.ok(new SetupSessionFormResponse(
            s.getRaceCode(), s.getCharacterName(), s.getCharacterPrompt(),
            s.getStartingSituation(), s.getArchetypeCode())))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @GetMapping( "/session/history" )
  public Collection<ChatEntry> getSessionHistory(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader) {
    return sessionService.getHistory(userHeader.getUserId());
  }

  @DeleteMapping( "/session/history" )
  public ResponseEntity<Void> deleteSessionHistory(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader) {
    sessionService.deleteHistory(userHeader.getUserId());
    return ResponseEntity.noContent().build();
  }

  @PutMapping( "/session/character-prompt" )
  public ResponseEntity<Void> updateCharacterPrompt(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestBody SetupSessionFieldRequest request) {
    CampaignSetupSession session = sessionService.getOrCreate(userHeader.getUserId());
    session.setCharacterPrompt(request.value());
    sessionService.save(userHeader.getUserId(), session);
    return ResponseEntity.noContent().build();
  }

  @PutMapping( "/session/starting-situation" )
  public ResponseEntity<Void> updateStartingSituation(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestBody SetupSessionFieldRequest request) {
    CampaignSetupSession session = sessionService.getOrCreate(userHeader.getUserId());
    session.setStartingSituation(request.value());
    sessionService.save(userHeader.getUserId(), session);
    return ResponseEntity.noContent().build();
  }

  @PutMapping( "/session/race-code" )
  public ResponseEntity<Void> updateRaceCode(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestBody SetupSessionFieldRequest request) {
    CampaignSetupSession session = sessionService.getOrCreate(userHeader.getUserId());
    session.setRaceCode(request.value());
    sessionService.save(userHeader.getUserId(), session);
    return ResponseEntity.noContent().build();
  }

  @PutMapping( "/session/character-name" )
  public ResponseEntity<Void> updateCharacterName(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestBody SetupSessionFieldRequest request) {
    CampaignSetupSession session = sessionService.getOrCreate(userHeader.getUserId());
    session.setCharacterName(request.value());
    sessionService.save(userHeader.getUserId(), session);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping( "/session" )
  public ResponseEntity<Void> deleteSession(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader) {
    sessionService.delete(userHeader.getUserId());
    return ResponseEntity.noContent().build();
  }
}
