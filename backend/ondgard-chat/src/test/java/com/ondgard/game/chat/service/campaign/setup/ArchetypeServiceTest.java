package com.ondgard.game.chat.service.campaign.setup;

import com.ondgard.game.chat.contract.campaign.setup.CampaignArchetypeResponse;
import com.ondgard.game.chat.model.setup.CampaignArchetype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class ArchetypeServiceTest {

  private ArchetypeService service;

  @BeforeEach
  void setUp() throws Exception {
    service = new ArchetypeService();
    service.init();
  }

  @Test
  void loadsArchetypesForAvailableLanguages() {
    assertThat(service.getRandomArchetypes("it", 100)).hasSize(4);
    assertThat(service.getRandomArchetypes("en", 100)).hasSize(4);
  }

  @Test
  void getRandomArchetypes_returnsRequestedCount() {
    Collection<CampaignArchetypeResponse> result = service.getRandomArchetypes("it", 3);
    assertThat(result).hasSize(3);
  }

  @Test
  void getRandomArchetypes_noDuplicates() {
    Collection<CampaignArchetypeResponse> result = service.getRandomArchetypes("it", 4);
    Collection codes = new HashSet<>(result.stream().map(CampaignArchetypeResponse::code).toList());
    assertThat(codes).hasSameSizeAs(result);
  }

  @Test
  void getRandomArchetypes_fallsBackToEnglishForUnknownLanguage() {
    Collection<CampaignArchetypeResponse> result = service.getRandomArchetypes("fr", 3);
    assertThat(result).hasSize(3);
    assertThat(result.iterator().next().description()).doesNotContain("Sopravvissuto");
  }

  @Test
  void getRandomArchetypes_countExceedsAvailable_returnsAll() {
    Collection<CampaignArchetypeResponse> result = service.getRandomArchetypes("it", 100);
    assertThat(result).hasSize(4);
  }

  @Test
  void getByCode_findsKnownCode() {
    CampaignArchetype result = service.getByCode("it", "SHIPWRECK_AMNESIA");
    assertThat(result).isNotNull();
    assertThat(result.code()).isEqualTo("SHIPWRECK_AMNESIA");
    assertThat(result.description()).contains("Sopravvissuto");
    assertThat(result.theme()).isNotBlank();
  }

  @Test
  void getByCode_returnsNullForUnknownCode() {
    assertThat(service.getByCode("it", "NONEXISTENT")).isNull();
  }

  @Test
  void resolveLanguage_returnsExactMatchIfAvailable() {
    assertThat(service.resolveLanguage("it")).isEqualTo("it");
    assertThat(service.resolveLanguage("en")).isEqualTo("en");
  }

  @Test
  void resolveLanguage_fallsBackToEnglish() {
    assertThat(service.resolveLanguage("fr")).isEqualTo("en");
    assertThat(service.resolveLanguage("de")).isEqualTo("en");
  }
}
