package com.ondgard.game.gateway.util;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

public final class LanguageUtils {

  private static final Collection<String> ISO_LANGUAGES = Set.of(Locale.getISOLanguages());
  private static final String DEFAULT_LANGUAGE = "en";

  private LanguageUtils() {
  }

  public static String resolveLanguageCode(String acceptLanguage) {
    if( acceptLanguage == null || acceptLanguage.isBlank() ){
      return DEFAULT_LANGUAGE;
    }
    String firstTag = acceptLanguage.split(",")[0].trim();
    if( firstTag.length() < 2 ){
      return DEFAULT_LANGUAGE;
    }
    String code = firstTag.substring(0, 2).toLowerCase(Locale.ROOT);
    return ISO_LANGUAGES.contains(code) ? code : DEFAULT_LANGUAGE;
  }
}
