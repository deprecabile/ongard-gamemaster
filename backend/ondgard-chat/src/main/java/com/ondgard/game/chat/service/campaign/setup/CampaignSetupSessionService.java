package com.ondgard.game.chat.service.campaign.setup;

import com.ondgard.game.chat.config.SetupSessionProperties;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.setup.CampaignSetupSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignSetupSessionService {

  private static final String KEY_PREFIX = "ondgard:setup-session:";

  private final RedisTemplate<String, CampaignSetupSession> setupSessionRedis;
  private final SetupSessionProperties setupSessionProperties;

  public CampaignSetupSession getOrCreate(String userHash) {
    String key = KEY_PREFIX + userHash;
    CampaignSetupSession session = setupSessionRedis.opsForValue().get(key);
    if( session != null ){
      setupSessionRedis.expire(key, setupSessionProperties.getTtl());
      return session;
    }
    session = CampaignSetupSession.builder()
        .userHash(userHash)
        .build();
    setupSessionRedis.opsForValue().set(key, session, setupSessionProperties.getTtl());
    log.debug("Setup session created for userHash={}", userHash);
    return session;
  }

  public Optional<CampaignSetupSession> get(String userHash) {
    String key = KEY_PREFIX + userHash;
    CampaignSetupSession session = setupSessionRedis.opsForValue().get(key);
    if( session != null ){
      setupSessionRedis.expire(key, setupSessionProperties.getTtl());
    }
    return Optional.ofNullable(session);
  }

  public void addMessage(String userHash, ChatEntry entry) {
    String key = KEY_PREFIX + userHash;
    CampaignSetupSession session = setupSessionRedis.opsForValue().get(key);
    if( session == null ){
      session = getOrCreate(userHash);
    }
    session.getHistory().add(entry);
    setupSessionRedis.opsForValue().set(key, session, setupSessionProperties.getTtl());
  }

  public Collection<ChatEntry> getHistory(String userHash) {
    String key = KEY_PREFIX + userHash;
    CampaignSetupSession session = setupSessionRedis.opsForValue().get(key);
    if( session != null ){
      setupSessionRedis.expire(key, setupSessionProperties.getTtl());
      return session.getHistory();
    }
    return List.of();
  }

  public void save(String userHash, CampaignSetupSession session) {
    setupSessionRedis.opsForValue().set(KEY_PREFIX + userHash, session, setupSessionProperties.getTtl());
  }

  public void deleteHistory(String userHash) {
    String key = KEY_PREFIX + userHash;
    CampaignSetupSession session = setupSessionRedis.opsForValue().get(key);
    if( session == null ) return;
    session.setHistory(new ArrayList<>());
    setupSessionRedis.opsForValue().set(key, session, setupSessionProperties.getTtl());
  }

  public boolean exists(String userHash) {
    return Boolean.TRUE.equals(setupSessionRedis.hasKey(KEY_PREFIX + userHash));
  }

  public void delete(String userHash) {
    setupSessionRedis.delete(KEY_PREFIX + userHash);
  }
}
