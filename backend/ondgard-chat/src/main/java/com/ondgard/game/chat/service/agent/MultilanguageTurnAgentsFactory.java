package com.ondgard.game.chat.service.agent;

import com.ondgard.game.chat.service.LoreProvider;
import com.ondgard.game.chat.service.SceneLoreProvider;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class MultilanguageTurnAgentsFactory {

  @Qualifier( "inventoryModel" )
  private final ChatModel inventoryModel;
  @Qualifier( "inventoryReviewerModel" )
  private final ChatModel inventoryReviewerModel;
  @Qualifier( "questModel" )
  private final ChatModel questModel;
  @Qualifier( "sceneModel" )
  private final ChatModel sceneModel;
  private final LoreProvider loreProvider;
  private final SceneLoreProvider sceneLoreProvider;

  private String inventoryReviewerTemplate;
  private String inventoryUpdaterTemplate;
  private String inventoryInitializerTemplate;
  private String questlogUpdaterTemplate;
  private String questlogInitializerTemplate;
  private String sceneInitializerTemplate;
  private String sceneUpdaterTemplate;

  @PostConstruct
  void loadTemplates() throws IOException {
    inventoryReviewerTemplate = new ClassPathResource("prompts/inventory_reviewer_system.md")
        .getContentAsString(StandardCharsets.UTF_8);
    log.debug("Inventory reviewer prompt template loaded ({} chars)", inventoryReviewerTemplate.length());

    inventoryUpdaterTemplate = new ClassPathResource("prompts/inventory_updater_system.md")
        .getContentAsString(StandardCharsets.UTF_8);
    log.debug("Inventory updater prompt template loaded ({} chars)", inventoryUpdaterTemplate.length());

    questlogUpdaterTemplate = new ClassPathResource("prompts/questlog_updater_system.md")
        .getContentAsString(StandardCharsets.UTF_8);
    log.debug("Questlog updater prompt template loaded ({} chars)", questlogUpdaterTemplate.length());

    inventoryInitializerTemplate = new ClassPathResource("prompts/inventory_initializer_system.md")
        .getContentAsString(StandardCharsets.UTF_8);
    log.debug("Inventory initializer prompt template loaded ({} chars)", inventoryInitializerTemplate.length());

    questlogInitializerTemplate = new ClassPathResource("prompts/questlog_initializer_system.md")
        .getContentAsString(StandardCharsets.UTF_8);
    log.debug("Questlog initializer prompt template loaded ({} chars)", questlogInitializerTemplate.length());

    sceneInitializerTemplate = new ClassPathResource("prompts/scene_initializer_system.md")
        .getContentAsString(StandardCharsets.UTF_8);
    log.debug("Scene initializer prompt template loaded ({} chars)", sceneInitializerTemplate.length());

    sceneUpdaterTemplate = new ClassPathResource("prompts/scene_updater_system.md")
        .getContentAsString(StandardCharsets.UTF_8);
    log.debug("Scene updater prompt template loaded ({} chars)", sceneUpdaterTemplate.length());
  }

  public TurnAgentsFactory forLanguage(String language) {
    String loreLangCode = loreProvider.resolveLanguage(language);
    String displayName = LanguageUtils.toDisplayName(language);

    String economyLore = loreProvider.get(loreLangCode).getLore("ECONOMIA");
    if( economyLore.isEmpty() ){
      economyLore = loreProvider.get(loreLangCode).getLore("ECONOMY");
    }

    final String sceneLore = sceneLoreProvider.getSceneLore(loreLangCode);

    return new LocalizedFactory(LocalizedFactory.LocalizedFactoryParams.builder()
        .inventoryModel(inventoryModel)
        .inventoryReviewerModel(inventoryReviewerModel)
        .questModel(questModel)
        .sceneModel(sceneModel)
        .inventoryReviewerPrompt(inventoryReviewerTemplate
            .replace("{{economyLore}}", economyLore))
        .inventoryUpdaterPrompt(inventoryUpdaterTemplate
            .replace("{{economyLore}}", economyLore)
            .replace("{{language}}", displayName))
        .inventoryInitializerPrompt(inventoryInitializerTemplate
            .replace("{{economyLore}}", economyLore)
            .replace("{{language}}", displayName))
        .questLogUpdaterPrompt(questlogUpdaterTemplate
            .replace("{{language}}", displayName))
        .questLogInitializerPrompt(questlogInitializerTemplate
            .replace("{{language}}", displayName))
        .sceneUpdaterPrompt(sceneUpdaterTemplate
            .replace("{{sceneLore}}", sceneLore)
            .replace("{{language}}", displayName))
        .sceneInitializerPrompt(sceneInitializerTemplate
            .replace("{{sceneLore}}", sceneLore)
            .replace("{{language}}", displayName))
        .build());
  }
}
