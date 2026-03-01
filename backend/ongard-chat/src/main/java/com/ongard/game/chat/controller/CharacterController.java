package com.ongard.game.chat.controller;

import com.ongard.game.chat.contract.PlayerCharacterSaveRequest;
import com.ongard.game.chat.model.PlayerCharacter;
import com.ongard.game.chat.service.CharacterService;
import com.ongard.game.header.GameUserHeader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping( "/api/character" )
@RequiredArgsConstructor
public class CharacterController {

  private final CharacterService characterService;

  @GetMapping( "/all" )
  public Collection<PlayerCharacter> getAllCharacters(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader) {
    return characterService.getAllCharacters(userHeader);
  }

  @GetMapping( "/{characterHash}" )
  public PlayerCharacter getCharacter(
      @PathVariable String characterHash,
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader) {
    return characterService.getCharacter(characterHash, userHeader);
  }

  @PostMapping
  public ResponseEntity<PlayerCharacter> createCharacter(
      @RequestBody PlayerCharacterSaveRequest request,
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader) {
    final PlayerCharacter created = characterService.createCharacter(request, userHeader);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }
}
