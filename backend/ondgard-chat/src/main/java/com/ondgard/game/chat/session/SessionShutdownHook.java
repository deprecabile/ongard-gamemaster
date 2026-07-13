package com.ondgard.game.chat.session;

import com.ondgard.game.chat.service.CampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty( name = "server.shutdown", havingValue = "graceful" )
public class SessionShutdownHook {

  private static final String ACTIVE_SESSIONS_KEY = "ondgard:active-sessions";

  private final StringRedisTemplate stringRedisTemplate;
  private final CampaignService campaignService;

  @EventListener( ContextClosedEvent.class )
  public void flushAllSessions() {
    log.info("Graceful shutdown: flushing all active sessions...");

    final Set<String> activeMembers;
    try{
      activeMembers = stringRedisTemplate.opsForZSet().range(ACTIVE_SESSIONS_KEY, 0, -1);
    }catch(Exception ex){
      log.error("Shutdown: cannot read active sessions from Redis: {}", ex.getMessage());
      return;
    }

    if( activeMembers == null || activeMembers.isEmpty() ){
      log.info("Shutdown: no active sessions to flush");
      return;
    }

    log.info("Shutdown: attempting to claim and flush {} active sessions", activeMembers.size());
    int flushed = 0;
    for( String characterHash : activeMembers ){
      try{
        campaignService.claimAndFlush(characterHash);
        flushed++;
      }catch(Exception ex){
        log.error("Shutdown: failed to flush session for characterHash={}: {}", characterHash, ex.getMessage(), ex);
      }
    }

    log.info("Shutdown complete: {}/{} sessions claimed", flushed, activeMembers.size());
  }
}
