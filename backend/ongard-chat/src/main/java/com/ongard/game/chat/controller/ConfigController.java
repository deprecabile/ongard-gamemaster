package com.ongard.game.chat.controller;

import com.ongard.game.chat.model.GameRace;
import com.ongard.game.chat.service.RaceService;
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
