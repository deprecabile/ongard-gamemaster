package com.ondgard.game.chat.controller;

import com.ondgard.game.chat.contract.PlayerCharacterSaveRequest;
import com.ondgard.game.chat.model.PlayerCharacter;
import com.ondgard.game.chat.service.CharacterService;
import com.ondgard.game.header.GameUserHeader;
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
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader, @PathVariable String characterHash) {
    return characterService.getCharacter(userHeader, characterHash);
  }

  @PostMapping
  public ResponseEntity<PlayerCharacter> createCharacter(
      @RequestHeader( GameUserHeader.HEADER_NAME ) GameUserHeader userHeader, @RequestBody PlayerCharacterSaveRequest request) {
    final PlayerCharacter created = characterService.createCharacter(userHeader, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }
}
