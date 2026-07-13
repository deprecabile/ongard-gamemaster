package com.ondgard.game.chat.session;

import com.ondgard.game.chat.service.CampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionStartupScan {

  private static final String ACTIVE_SESSIONS_KEY = "ondgard:active-sessions";
  private static final String TIMER_PREFIX = "ondgard:session-timer:";

  private final StringRedisTemplate stringRedisTemplate;
  private final CampaignService campaignService;

  @Async
  @EventListener( ApplicationReadyEvent.class )
  public void scanOrphanedSessions() {
    log.debug("Scanning for orphaned sessions...");

    Set<String> activeMembers = stringRedisTemplate.opsForZSet().range(ACTIVE_SESSIONS_KEY, 0, -1);
    if( activeMembers == null || activeMembers.isEmpty() ){
      log.debug("No active sessions found");
      return;
    }

    log.info("Found {} active sessions, checking timers", activeMembers.size());
    int flushed = 0;
    for( String characterHash : activeMembers ){
      Boolean timerExists = stringRedisTemplate.hasKey(TIMER_PREFIX + characterHash);
      if( timerExists == null || !timerExists ){
        log.warn("Orphaned session detected for characterHash={}, flushing", characterHash);
        try{
          campaignService.claimAndFlush(characterHash);
          flushed++;
        }catch(Exception ex){
          log.error("Failed to flush orphaned session for characterHash={}: {}", characterHash, ex.getMessage(), ex);
        }
      }
    }

    log.info("Startup scan complete: {} orphaned sessions flushed out of {} active", flushed, activeMembers.size());
  }
}
