package com.ondgard.game.gateway.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageUtilsTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource( strings = {"   "} )
  void shouldReturnEnForNullBlankOrEmpty(String input) {
    assertEquals("en", LanguageUtils.resolveLanguageCode(input));
  }

  @ParameterizedTest
  @CsvSource( {
      "en, en",
      "it, it",
      "it-IT, it",
      "'it-IT,it;q=0.9,en;q=0.8', it",
      "EN, en",
  } )
  void shouldExtractValidLanguageCode(String input, String expected) {
    assertEquals(expected, LanguageUtils.resolveLanguageCode(input));
  }

  @Test
  void shouldReturnEnForInvalidIsoCode() {
    assertEquals("en", LanguageUtils.resolveLanguageCode("xx"));
  }

  @Test
  void shouldReturnEnForTooShortInput() {
    assertEquals("en", LanguageUtils.resolveLanguageCode("a"));
  }
}
