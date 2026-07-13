package com.ondgard.game.chat.session;

import com.ondgard.game.chat.service.CampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionExpirationListener implements MessageListener {

  private static final String TIMER_PREFIX = "ondgard:session-timer:";

  private final CampaignService campaignService;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try{
      String expiredKey = new String(message.getBody());
      if( !expiredKey.startsWith(TIMER_PREFIX) ){
        return;
      }

      String characterHash = expiredKey.substring(TIMER_PREFIX.length());
      log.info("Session timer expired for characterHash={}", characterHash);
      campaignService.claimAndFlush(characterHash);
    }catch(Exception ex){
      log.error("Error handling session expiration: {}", ex.getMessage(), ex);
    }
  }
}
