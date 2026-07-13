package com.ondgard.game.chat.service.campaign.setup;

import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.setup.CampaignSetupSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CampaignSetupSessionServiceTest extends TestcontainersConfiguration {

  private static final String KEY_PREFIX = "ondgard:setup-session:";

  @Autowired private CampaignSetupSessionService sessionService;
  @Autowired private RedisTemplate<String, CampaignSetupSession> setupSessionRedis;

  private final List<String> createdUserHashes = new ArrayList<>();

  @AfterEach
  void cleanup() {
    for( String userHash : createdUserHashes ){
      setupSessionRedis.delete(KEY_PREFIX + userHash);
    }
    createdUserHashes.clear();
  }

  private String newUserHash() {
    String hash = "test-user-" + System.nanoTime();
    createdUserHashes.add(hash);
    return hash;
  }

  // ===== getOrCreate =====

  @Test
  void getOrCreate_newSession_createsWithEmptyHistory() {
    String userHash = newUserHash();

    CampaignSetupSession session = sessionService.getOrCreate(userHash);

    assertThat(session).isNotNull();
    assertThat(session.getUserHash()).isEqualTo(userHash);
    assertThat(session.getHistory()).isEmpty();
  }

  @Test
  void getOrCreate_existingSession_returnsSameSession() {
    String userHash = newUserHash();

    CampaignSetupSession first = sessionService.getOrCreate(userHash);
    sessionService.addMessage(userHash, new ChatEntry(0, "hello", "world"));

    CampaignSetupSession second = sessionService.getOrCreate(userHash);

    assertThat(second.getUserHash()).isEqualTo(first.getUserHash());
    assertThat(second.getHistory()).hasSize(1);
  }

  @Test
  void getOrCreate_setsTtl() {
    String userHash = newUserHash();

    sessionService.getOrCreate(userHash);

    Long ttl = setupSessionRedis.getExpire(KEY_PREFIX + userHash, TimeUnit.SECONDS);
    assertThat(ttl).isGreaterThan(1100); // ~20 min = 1200s, with some margin
    assertThat(ttl).isLessThanOrEqualTo(1200);
  }

  // ===== get =====

  @Test
  void get_existing_returnsSession() {
    String userHash = newUserHash();
    sessionService.getOrCreate(userHash);

    Optional<CampaignSetupSession> result = sessionService.get(userHash);

    assertThat(result).isPresent();
    assertThat(result.get().getUserHash()).isEqualTo(userHash);
  }

  @Test
  void get_nonExisting_returnsEmpty() {
    Optional<CampaignSetupSession> result = sessionService.get("non-existing-hash");

    assertThat(result).isEmpty();
  }

  @Test
  void get_refreshesTtl() {
    String userHash = newUserHash();
    sessionService.getOrCreate(userHash);

    // Artificially reduce TTL
    setupSessionRedis.expire(KEY_PREFIX + userHash, 60, TimeUnit.SECONDS);
    Long reducedTtl = setupSessionRedis.getExpire(KEY_PREFIX + userHash, TimeUnit.SECONDS);
    assertThat(reducedTtl).isLessThanOrEqualTo(60);

    // get() should refresh TTL back to ~20min
    sessionService.get(userHash);

    Long refreshedTtl = setupSessionRedis.getExpire(KEY_PREFIX + userHash, TimeUnit.SECONDS);
    assertThat(refreshedTtl).isGreaterThan(1100);
  }

  // ===== addMessage =====

  @Test
  void addMessage_existingSession_appendsToHistory() {
    String userHash = newUserHash();
    sessionService.getOrCreate(userHash);

    sessionService.addMessage(userHash, new ChatEntry(0, "question 1", "answer 1"));
    sessionService.addMessage(userHash, new ChatEntry(0, "question 2", "answer 2"));

    Collection<ChatEntry> history = sessionService.getHistory(userHash);
    assertThat(history).hasSize(2);
  }

  @Test
  void addMessage_noSession_createsSessionAndAdds() {
    String userHash = newUserHash();

    sessionService.addMessage(userHash, new ChatEntry(0, "first msg", "first response"));

    Collection<ChatEntry> history = sessionService.getHistory(userHash);
    assertThat(history).hasSize(1);
  }

  @Test
  void addMessage_refreshesTtl() {
    String userHash = newUserHash();
    sessionService.getOrCreate(userHash);

    // Reduce TTL
    setupSessionRedis.expire(KEY_PREFIX + userHash, 60, TimeUnit.SECONDS);

    sessionService.addMessage(userHash, new ChatEntry(0, "msg", "resp"));

    Long ttl = setupSessionRedis.getExpire(KEY_PREFIX + userHash, TimeUnit.SECONDS);
    assertThat(ttl).isGreaterThan(1100);
  }

  // ===== getHistory =====

  @Test
  void getHistory_existing_returnsHistory() {
    String userHash = newUserHash();
    sessionService.getOrCreate(userHash);
    sessionService.addMessage(userHash, new ChatEntry(1, "q", "a"));

    Collection<ChatEntry> history = sessionService.getHistory(userHash);

    assertThat(history).hasSize(1);
    ChatEntry entry = history.iterator().next();
    assertThat(entry.userMessage()).isEqualTo("q");
    assertThat(entry.gmResponse()).isEqualTo("a");
  }

  @Test
  void getHistory_nonExisting_returnsEmptyList() {
    Collection<ChatEntry> history = sessionService.getHistory("non-existing-hash");

    assertThat(history).isEmpty();
  }

  @Test
  void getHistory_refreshesTtl() {
    String userHash = newUserHash();
    sessionService.getOrCreate(userHash);

    setupSessionRedis.expire(KEY_PREFIX + userHash, 60, TimeUnit.SECONDS);

    sessionService.getHistory(userHash);

    Long ttl = setupSessionRedis.getExpire(KEY_PREFIX + userHash, TimeUnit.SECONDS);
    assertThat(ttl).isGreaterThan(1100);
  }

  // ===== delete =====

  @Test
  void delete_existingSession_removesFromRedis() {
    String userHash = newUserHash();
    sessionService.getOrCreate(userHash);

    sessionService.delete(userHash);

    assertThat(setupSessionRedis.hasKey(KEY_PREFIX + userHash)).isFalse();
    assertThat(sessionService.get(userHash)).isEmpty();
  }

  @Test
  void delete_nonExistingSession_doesNotThrow() {
    sessionService.delete("non-existing-hash");
    // no exception = success
  }

  // ===== uniqueness =====

  @Test
  void sameUserHash_sameSession() {
    String userHash = newUserHash();

    sessionService.getOrCreate(userHash);
    sessionService.addMessage(userHash, new ChatEntry(0, "msg1", "resp1"));

    // Second getOrCreate returns session with the message
    CampaignSetupSession session = sessionService.getOrCreate(userHash);
    assertThat(session.getHistory()).hasSize(1);
  }

  @Test
  void differentUserHashes_independentSessions() {
    String userHash1 = newUserHash();
    String userHash2 = newUserHash();

    sessionService.getOrCreate(userHash1);
    sessionService.addMessage(userHash1, new ChatEntry(0, "msg1", "resp1"));

    sessionService.getOrCreate(userHash2);

    assertThat(sessionService.getHistory(userHash1)).hasSize(1);
    assertThat(sessionService.getHistory(userHash2)).isEmpty();
  }
}
