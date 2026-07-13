package com.ondgard.game.chat.service.agent.setup;

import com.ondgard.game.chat.model.GameRace;
import com.ondgard.game.chat.service.LoreProvider;
import com.ondgard.game.chat.service.RaceService;
import com.ondgard.game.chat.service.rag.LoreRetrievalService;
import com.ondgard.game.chat.util.LanguageUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignSetupAgentsFactory {

  @Qualifier( "setupAdvisorModel" )
  private final ChatModel setupAdvisorModel;
  @Qualifier( "setupSummaryModel" )
  private final ChatModel setupSummaryModel;
  @Qualifier( "setupGeneratorModel" )
  private final ChatModel setupGeneratorModel;
  @Qualifier( "nameRaceSetupModel" )
  private final ChatModel nameRaceSetupModel;
  private final LoreRetrievalService loreRetrievalService;
  private final LoreProvider loreProvider;
  private final RaceService raceService;

  private String advisorTemplate;
  private String summaryTemplate;
  private String generatorCharacterTemplate;
  private String generatorSceneTemplate;
  private String nameRaceSetupTemplate;

  @PostConstruct
  void loadTemplates() throws IOException {
    advisorTemplate = new ClassPathResource("prompts/campaign_setup_advisor_system.md")
        .getContentAsString(StandardCharsets.UTF_8);
    log.debug("Setup advisor prompt template loaded ({} chars)", advisorTemplate.length());

    summaryTemplate = new ClassPathResource("prompts/campaign_setup_summary_system.md")
        .getContentAsString(StandardCharsets.UTF_8);
    log.debug("Setup summary prompt template loaded ({} chars)", summaryTemplate.length());

    generatorCharacterTemplate = new ClassPathResource("prompts/campaign_setup_generator_character_system.md")
        .getContentAsString(StandardCharsets.UTF_8);
    log.debug("Setup generator character prompt template loaded ({} chars)", generatorCharacterTemplate.length());

    generatorSceneTemplate = new ClassPathResource("prompts/campaign_setup_generator_scene_system.md")
        .getContentAsString(StandardCharsets.UTF_8);
    log.debug("Setup generator scene prompt template loaded ({} chars)", generatorSceneTemplate.length());

    String rawTemplate = new ClassPathResource("prompts/name_race_setup_system.md")
        .getContentAsString(StandardCharsets.UTF_8);

    String raceMapping = raceService.getAllRaces().stream()
        .map(r -> r.getCode() + " = " + r.getName())
        .collect(Collectors.joining(", "));
    nameRaceSetupTemplate = rawTemplate.replace("{{raceMapping}}", raceMapping);

    log.debug("Name/race setup prompt template loaded and compiled ({} chars)", nameRaceSetupTemplate.length());
  }

  public CampaignSetupAdvisorAgent buildAdvisorAgent(String language,
                                                     String characterPrompt,
                                                     String startingSituation,
                                                     String raceCode,
                                                     String characterName) {
    String loreLangCode = loreProvider.resolveLanguage(language);
    String displayName = LanguageUtils.toDisplayName(language);

    String raceName = "";
    if( raceCode != null && !raceCode.isBlank() ){
      GameRace race = raceService.getRace(raceCode);
      if( race != null ){
        raceName = race.getName();
      }
    }

    String systemPrompt = advisorTemplate
        .replace("{{language}}", displayName)
        .replace("{{characterName}}", characterName != null ? characterName : "")
        .replace("{{raceName}}", raceName)
        .replace("{{characterPrompt}}", characterPrompt != null ? characterPrompt : "")
        .replace("{{startingSituation}}", startingSituation != null ? startingSituation : "");

    var tools = new CampaignSetupAdvisorTools(loreRetrievalService, loreLangCode);
    return new CampaignSetupAdvisorAgent(setupAdvisorModel, tools, systemPrompt);
  }

  public CampaignSetupSummaryAgent buildSummaryAgent(String language) {
    String displayName = LanguageUtils.toDisplayName(language);

    String systemPrompt = summaryTemplate
        .replace("{{language}}", displayName);

    return new CampaignSetupSummaryAgent(setupSummaryModel, systemPrompt);
  }

  public CampaignSetupGeneratorAgent buildGeneratorAgent(String language) {
    String loreLangCode = loreProvider.resolveLanguage(language);
    String displayName = LanguageUtils.toDisplayName(language);

    String characterPrompt = generatorCharacterTemplate
        .replace("{{language}}", displayName);
    String scenePrompt = generatorSceneTemplate
        .replace("{{language}}", displayName);

    var tools = new CampaignSetupGeneratorTools(loreRetrievalService, loreLangCode);
    return new CampaignSetupGeneratorAgent(setupGeneratorModel, tools, characterPrompt, scenePrompt);
  }

  public NameRaceSetupAgent buildNameRaceSetupAgent(String language) {
    String loreLangCode = loreProvider.resolveLanguage(language);
    var tools = new CampaignSetupGeneratorTools(loreRetrievalService, loreLangCode);
    return new NameRaceSetupAgent(nameRaceSetupModel, tools, nameRaceSetupTemplate);
  }
}
