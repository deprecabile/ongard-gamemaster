package com.ondgard.game.chat.service;

import com.google.gson.Gson;
import com.ondgard.game.GameGsonFactory;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.SummaryPendingResult;
import com.ondgard.game.chat.repository.CampaignRepository;
import com.ondgard.game.chat.service.agent.SummaryAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith( MockitoExtension.class )
class SummaryServiceTest {

  @Mock private SummaryAgent summaryAgent;
  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private CampaignRepository campaignRepository;
  @Mock private ValueOperations<String, String> valueOps;

  private final Gson gson = GameGsonFactory.build();
  private SummaryService summaryService;

  @BeforeEach
  void setUp() {
    summaryService = new SummaryService(summaryAgent, stringRedisTemplate, campaignRepository);
    lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
  }

  private static final List<ChatEntry> SAMPLE_ENTRIES = List.of(
      new ChatEntry(1, "action", "response")
  );

  @Test
  void generate_sessionActive_writesPendingToRedis() {
    when(summaryAgent.generate(anyString(), eq("old summary"), anyString())).thenReturn("new summary");
    when(stringRedisTemplate.hasKey("ondgard:ctx:char123")).thenReturn(true);

    summaryService.generate("italiano", SAMPLE_ENTRIES, "old summary", 2, 100L, "char123");

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

    assertThat(keyCaptor.getValue()).isEqualTo("ondgard:summary-pending:char123");
    assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofHours(1));

    SummaryPendingResult result = gson.fromJson(valueCaptor.getValue(), SummaryPendingResult.class);
    assertThat(result.narrativeSummary()).isEqualTo("new summary");
    assertThat(result.summaryVersion()).isEqualTo(2);

    verifyNoInteractions(campaignRepository);
  }

  @Test
  void generate_sessionExpired_writesDirectlyToDb() {
    when(summaryAgent.generate(anyString(), any(), anyString())).thenReturn("first summary");
    when(stringRedisTemplate.hasKey("ondgard:ctx:char456")).thenReturn(false);

    CampaignEntity entity = CampaignEntity.builder().characterId(200L).build();
    when(campaignRepository.findById(200L)).thenReturn(Optional.of(entity));

    summaryService.generate("italiano", SAMPLE_ENTRIES, null, 1, 200L, "char456");

    verify(campaignRepository).save(entity);
    assertThat(entity.getNarrativeSummary()).isEqualTo("first summary");
    assertThat(entity.getSummaryVersion()).isEqualTo(1);

    verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void generate_agentThrows_doesNotPropagate() {
    when(summaryAgent.generate(anyString(), any(), anyString())).thenThrow(new RuntimeException("LLM error"));

    summaryService.generate("italiano", SAMPLE_ENTRIES, "summary", 1, 100L, "charErr");

    verifyNoInteractions(campaignRepository);
    verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void generate_nullCurrentSummary_passesNullToAgent() {
    when(summaryAgent.generate(anyString(), any(), anyString())).thenReturn("generated");
    when(stringRedisTemplate.hasKey("ondgard:ctx:charNull")).thenReturn(true);

    summaryService.generate("italiano", SAMPLE_ENTRIES, null, 1, 100L, "charNull");

    verify(summaryAgent).generate(anyString(), isNull(), anyString());
    verify(valueOps).set(anyString(), anyString(), any(Duration.class));
  }
}
