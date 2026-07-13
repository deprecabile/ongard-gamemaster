package com.ondgard.game.chat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoreProviderTest {

  private LoreProvider loreProvider;

  @BeforeEach
  void setUp() throws Exception {
    loreProvider = new LoreProvider();
    loreProvider.loadAllLore();
  }

  @Test
  void discoversAvailableLanguages() {
    assertThat(loreProvider.getAvailableLanguages()).contains("it", "en");
  }

  @Test
  void get_returnsLoreRegistryForLanguage() {
    LoreRegistry itRegistry = loreProvider.get("it");
    assertThat(itRegistry).isNotNull();
    assertThat(itRegistry.getCategoryKeys()).containsExactlyInAnyOrder("GEOGRAFIA", "STORIA");

    LoreRegistry enRegistry = loreProvider.get("en");
    assertThat(enRegistry).isNotNull();
    assertThat(enRegistry.getCategoryKeys()).containsExactly("GEOGRAPHY");
  }

  @Test
  void get_loreContentIsCorrect() {
    LoreRegistry itRegistry = loreProvider.get("it");
    assertThat(itRegistry.getLore("GEOGRAFIA")).contains("montagne del nord");
    assertThat(itRegistry.getLore("STORIA")).contains("guerra antica");

    LoreRegistry enRegistry = loreProvider.get("en");
    assertThat(enRegistry.getLore("GEOGRAPHY")).contains("northern mountains");
  }

  @Test
  void get_getAllLoreContainsAllCategories() {
    String allLore = loreProvider.get("it").getAllLore();
    assertThat(allLore).contains("# GEOGRAFIA");
    assertThat(allLore).contains("# STORIA");
    assertThat(allLore).contains("montagne del nord");
    assertThat(allLore).contains("guerra antica");
  }

  @Test
  void get_returnsEmptyStringForUnknownCategory() {
    assertThat(loreProvider.get("it").getLore("NONEXISTENT")).isEmpty();
  }

  @Test
  void resolveLanguage_returnsExactMatchIfAvailable() {
    assertThat(loreProvider.resolveLanguage("it")).isEqualTo("it");
    assertThat(loreProvider.resolveLanguage("en")).isEqualTo("en");
  }

  @Test
  void resolveLanguage_fallsBackToEnglish() {
    assertThat(loreProvider.resolveLanguage("fr")).isEqualTo("en");
    assertThat(loreProvider.resolveLanguage("de")).isEqualTo("en");
  }

  @Test
  void getContentHash_isNotNullOrEmpty() {
    assertThat(loreProvider.getContentHash()).isNotNull().isNotEmpty();
  }

  @Test
  void getContentHash_isDeterministic() throws Exception {
    var another = new LoreProvider();
    another.loadAllLore();
    assertThat(loreProvider.getContentHash()).isEqualTo(another.getContentHash());
  }
}
