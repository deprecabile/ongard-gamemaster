package com.ondgard.game.chat.controller;

import com.ondgard.game.chat.contract.AdvisorLogResponse;
import com.ondgard.game.chat.contract.CampaignListItem;
import com.ondgard.game.chat.contract.CampaignTurnResponse;
import com.ondgard.game.chat.contract.PlayerNotesUpdateRequest;
import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.service.AdvisorLogService;
import com.ondgard.game.chat.service.CampaignService;
import com.ondgard.game.chat.service.CharacterService;
import com.ondgard.game.chat.service.PlayerNotesService;
import com.ondgard.game.exception.ForbiddenException;
import com.ondgard.game.exception.NoResultException;
import com.ondgard.game.header.GameUserHeader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping( "/api/campaign" )
@RequiredArgsConstructor
public class CampaignController {

  private final CampaignService campaignService;
  private final CharacterService characterService;
  private final AdvisorLogService advisorLogService;
  private final PlayerNotesService playerNotesService;

  @GetMapping( "/turn" )
  public ResponseEntity<CampaignTurnResponse> getTurn(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestParam String characterHash) {
    return ResponseEntity.ok(campaignService.getTurn(userHeader, characterHash));
  }

  @GetMapping( "/exists" )
  public boolean hasCampaigns(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader) {
    return campaignService.hasCampaigns(userHeader);
  }

  @GetMapping( "/list" )
  public List<CampaignListItem> listCampaigns(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader) {
    return campaignService.listCampaigns(userHeader);
  }

  @GetMapping( "/history" )
  public List<ChatEntry> getHistory(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestParam String characterHash) {
    return campaignService.getHistory(userHeader, characterHash);
  }

  @GetMapping( "/quest-active" )
  public ResponseEntity<String> getQuestActive(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestParam String characterHash) {
    String questActive = campaignService.getQuestActive(userHeader, characterHash);
    return ResponseEntity.ok(questActive);
  }

  @DeleteMapping( "/{characterHash}" )
  public ResponseEntity<Void> deleteCampaign(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @PathVariable String characterHash) {
    campaignService.deleteCampaign(userHeader, characterHash);
    return ResponseEntity.ok().build();
  }

  @GetMapping( "/advisor-log" )
  public List<AdvisorLogResponse> getAdvisorLog(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestParam String characterHash) {
    try{
      characterService.getCharacter(userHeader, characterHash);
    }catch(NoResultException ex){
      throw new ForbiddenException("Access denied");
    }
    CampaignContext ctx = campaignService.load(
        UUID.fromString(userHeader.getUserId()), characterHash);
    return advisorLogService.findRecent(ctx.getCampaignId()).stream()
        .map(p -> new AdvisorLogResponse(p.turnNumber(), p.userMessage(), p.advisorResponse(), p.created()))
        .toList();
  }

  @PostMapping( "/session/end" )
  public ResponseEntity<Void> endSession(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestParam String characterHash) {
    try{
      characterService.getCharacter(userHeader, characterHash);
    }catch(NoResultException ex){
      throw new ForbiddenException("Access denied");
    }
    campaignService.flush(characterHash);
    return ResponseEntity.ok().build();
  }

  @GetMapping( "/playerNotes" )
  public ResponseEntity<String> getPlayerNotes(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestParam String characterHash) {
    return ResponseEntity.ok(playerNotesService.getPlayerNotes(userHeader, characterHash));
  }

  @PutMapping( "/playerNotes" )
  public ResponseEntity<Void> updatePlayerNotes(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader,
      @RequestBody PlayerNotesUpdateRequest request) {
    playerNotesService.updatePlayerNotes(userHeader, request.characterHash(), request.newNotesSnapshot());
    return ResponseEntity.ok().build();
  }
}
