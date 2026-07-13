package com.ondgard.game.chat.service.campaign.setup;

import com.ondgard.game.GameGsonFactory;
import com.ondgard.game.chat.contract.campaign.setup.CampaignArchetypeResponse;
import com.ondgard.game.chat.model.setup.CampaignArchetype;
import jakarta.annotation.PostConstruct;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@NoArgsConstructor
public class ArchetypeService {

  private static final String PREFIX = "game_data/ondgard/";

  private final Map<String, List<CampaignArchetype>> archetypesByLang = new ConcurrentHashMap<>();

  @PostConstruct
  void init() throws IOException {
    var resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources = resolver.getResources("classpath:game_data/ondgard/*/archetypes.json");

    for( Resource resource : resources ){
      String uri = resource.getURI().toString();
      int prefixIdx = uri.indexOf(PREFIX);
      if( prefixIdx == -1 ) continue;

      String afterPrefix = uri.substring(prefixIdx + PREFIX.length());
      String lang = afterPrefix.substring(0, afterPrefix.indexOf('/'));

      String content = resource.getContentAsString(StandardCharsets.UTF_8);
      CampaignArchetype[] array = GameGsonFactory.build().fromJson(content, CampaignArchetype[].class);
      archetypesByLang.put(lang, List.of(array));

      log.info("Archetypes loaded for lang={} ({} entries)", lang, array.length);
    }
  }

  public Collection<CampaignArchetypeResponse> getRandomArchetypes(String lang, int count) {
    Collection<CampaignArchetype> all = archetypesByLang.get(resolveLanguage(lang));
    if( all == null || all.isEmpty() ) return List.of();

    var shuffled = new ArrayList<>(all);
    Collections.shuffle(shuffled, ThreadLocalRandom.current());
    return shuffled.subList(0, Math.min(count, shuffled.size())).stream()
        .map(s -> new CampaignArchetypeResponse(s.code(), s.description()))
        .toList();
  }

  public CampaignArchetype getByCode(String lang, String code) {
    List<CampaignArchetype> all = archetypesByLang.get(resolveLanguage(lang));
    if( all == null ) return null;
    return all.stream()
        .filter(a -> a.code().equals(code))
        .findFirst()
        .orElse(null);
  }

  String resolveLanguage(String requested) {
    if( archetypesByLang.containsKey(requested) ) return requested;
    if( archetypesByLang.containsKey("en") ) return "en";
    return archetypesByLang.keySet().iterator().next();
  }
}
