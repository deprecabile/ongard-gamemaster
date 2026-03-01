package com.ongard.game.chat.service;

import com.ongard.game.chat.model.GameRace;
import com.ongard.game.chat.repository.CfgRaceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RaceService {

  private final CfgRaceRepository cfgRaceRepository;

  private volatile Map<String, GameRace> raceCache;

  @PostConstruct
  void loadRaces() {
    var races = new LinkedHashMap<String, GameRace>();
    cfgRaceRepository.findAll().forEach(entity -> {
      GameRace race = GameRace.builder()
          .code(entity.getCode())
          .name(entity.getName())
          .description(entity.getDescription())
          .attributes(entity.getAttributes())
          .build();
      races.put(entity.getCode(), race);
    });
    this.raceCache = Map.copyOf(races);
  }

  public Collection<GameRace> getAllRaces() {
    return raceCache.values();
  }

  public GameRace getRace(String code) {
    return raceCache.get(code);
  }
}
