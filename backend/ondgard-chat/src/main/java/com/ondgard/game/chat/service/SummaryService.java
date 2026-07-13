package com.ondgard.game.chat.service;

import com.google.gson.Gson;
import com.ondgard.game.GameGsonFactory;
import com.ondgard.game.chat.config.ai.TokenTrackingContext;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.SummaryPendingResult;
import com.ondgard.game.chat.repository.CampaignRepository;
import com.ondgard.game.chat.service.agent.SummaryAgent;
import com.ondgard.game.chat.util.ChatEntryFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryService {

  static final String PENDING_PREFIX = "ondgard:summary-pending:";
  private static final String CTX_PREFIX = "ondgard:ctx:";
  private static final Duration PENDING_TTL = Duration.ofHours(1);

  private final SummaryAgent summaryAgent;
  private final StringRedisTemplate stringRedisTemplate;
  private final CampaignRepository campaignRepository;

  @Async
  public void generateAsync(String language, List<ChatEntry> bufferEntries, String currentSummary,
                            int newVersion, Long campaignId, String characterHash) {
    generate(language, bufferEntries, currentSummary, newVersion, campaignId, characterHash);
  }

  public void generate(String language, List<ChatEntry> bufferEntries, String currentSummary,
                       int newVersion, Long campaignId, String characterHash) {
    TokenTrackingContext.set(characterHash);
    try{
      log.info("Summary generation started for characterHash={}, version={}", characterHash, newVersion);

      String bufferSnapshot = ChatEntryFormatter.toTextBuffer(bufferEntries);
      String newSummary = summaryAgent.generate(language, currentSummary, bufferSnapshot);

      log.info("Summary generated for characterHash={} ({} chars), version={}",
          characterHash, newSummary != null ? newSummary.length() : 0, newVersion);

      Gson gson = GameGsonFactory.build();
      var pendingResult = new SummaryPendingResult(newSummary, newVersion);
      String json = gson.toJson(pendingResult);

      // Check if session is still active in Redis
      Boolean ctxExists = stringRedisTemplate.hasKey(CTX_PREFIX + characterHash);
      if( Boolean.TRUE.equals(ctxExists) ){
        // Session active — write pending result for CampaignService to pick up
        stringRedisTemplate.opsForValue().set(PENDING_PREFIX + characterHash, json, PENDING_TTL);
        log.debug("Summary pending result written to Redis for characterHash={}", characterHash);
      } else{
        // Session ended — write directly to DB
        campaignRepository.findById(campaignId).ifPresent(entity -> {
          entity.setNarrativeSummary(newSummary);
          entity.setSummaryVersion(newVersion);
          campaignRepository.save(entity);
          log.info("Summary written directly to DB for campaignId={} (session expired)", campaignId);
        });
      }
    }catch(Exception ex){
      log.error("Summary generation failed for characterHash={}: {}", characterHash, ex.getMessage(), ex);
    }finally{
      TokenTrackingContext.clear();
    }
  }
}
