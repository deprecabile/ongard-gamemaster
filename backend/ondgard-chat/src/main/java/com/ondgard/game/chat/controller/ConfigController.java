package com.ondgard.game.chat.controller;

import com.ondgard.game.chat.model.GameRace;
import com.ondgard.game.chat.service.RaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping( "/api/config" )
@RequiredArgsConstructor
public class ConfigController {

  private final RaceService raceService;

  @GetMapping( "/races" )
  public Collection<GameRace> getRaces() {
    return raceService.getAllRaces();
  }
}
