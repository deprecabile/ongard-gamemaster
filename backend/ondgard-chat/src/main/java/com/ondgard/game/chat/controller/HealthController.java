package com.ondgard.game.chat.controller;

import com.ondgard.game.chat.service.rag.RagReadinessGate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping( "/api" )
public class HealthController {

  private final RagReadinessGate ragReadinessGate;

  @GetMapping( "/health" )
  public Map<String, Boolean> health() {
    return Map.of("available", ragReadinessGate.isReady());
  }
}
