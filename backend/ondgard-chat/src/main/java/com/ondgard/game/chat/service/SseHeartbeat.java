package com.ondgard.game.chat.service;

import com.ondgard.game.chat.contract.sse.SseEventType;
import com.ondgard.game.chat.contract.sse.SseProgressCode;
import com.ondgard.game.chat.contract.sse.SseProgressEvent;
import com.ondgard.game.chat.contract.sse.SseProgressValue;
import com.ondgard.game.chat.contract.sse.campaign.ask.SseAdvisorEventType;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SseHeartbeat implements AutoCloseable {

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  SseHeartbeat(SseEmitter emitter, long intervalSeconds, SseProgressCode code) {
    this(emitter, intervalSeconds, SseEventType.PROGRESS.getValue(), code);
  }

  SseHeartbeat(SseEmitter emitter, long intervalSeconds, SseAdvisorEventType type, SseProgressCode code) {
    this(emitter, intervalSeconds, type.getValue(), code);
  }

  public SseHeartbeat(SseEmitter emitter, long intervalSeconds, String eventTypeName, SseProgressValue code) {
    scheduler.scheduleAtFixedRate(() -> {
      try{
        emitter.send(SseEmitter.event()
            .name(eventTypeName)
            .data(new SseProgressEvent(LocalDateTime.now(), code.getValue()), MediaType.APPLICATION_JSON));
      }catch(IOException ignored){
        // client disconnesso — verra' stoppato dal close()
      }
    }, 0, intervalSeconds, TimeUnit.SECONDS);
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
  }
}
