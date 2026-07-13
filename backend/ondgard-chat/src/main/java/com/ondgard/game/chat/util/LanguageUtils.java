package com.ondgard.game.chat.util;

import java.util.Locale;

public final class LanguageUtils {

  private LanguageUtils() {
  }

  public static String toDisplayName(String langCode) {
    Locale locale = Locale.of(langCode);
    String name = locale.getDisplayLanguage(locale);
    return (name == null || name.isBlank()) ? "English" : name;
  }
}
