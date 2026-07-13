package com.ondgard.game.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTemplateServiceTest {

  private EmailTemplateService service;

  @BeforeEach
  void setUp() throws IOException {
    service = new EmailTemplateService();
    service.loadTemplates();
  }

  @Test
  void render_confirmationEn_replacesPlaceholders() {
    String result = service.render("en", "confirmation", Map.of(
        "username", "TestHero",
        "confirmUrl", "https://ondgard.com/confirm?token=abc123"
    ));

    assertThat(result).contains("TestHero");
    assertThat(result).contains("https://ondgard.com/confirm?token=abc123");
    assertThat(result).doesNotContain("{{");
  }

  @Test
  void render_alreadyRegisteredEn_replacesPlaceholders() {
    String result = service.render("en", "already-registered", Map.of(
        "username", "ExistingUser",
        "loginUrl", "https://ondgard.com/login"
    ));

    assertThat(result).contains("ExistingUser");
    assertThat(result).contains("https://ondgard.com/login");
    assertThat(result).doesNotContain("{{");
  }

  @Test
  void render_confirmationIt_containsItalianText() {
    String result = service.render("it", "confirmation", Map.of(
        "username", "Giocatore",
        "confirmUrl", "https://ondgard.com/confirm?token=xyz"
    ));

    assertThat(result).contains("Benvenuto");
  }

  @Test
  void render_alreadyRegisteredIt_containsItalianText() {
    String result = service.render("it", "already-registered", Map.of(
        "username", "Giocatore",
        "loginUrl", "https://ondgard.com/login"
    ));

    assertThat(result).contains("Tentativo");
  }

  @Test
  void render_confirmationEs_containsSpanishText() {
    String result = service.render("es", "confirmation", Map.of(
        "username", "Jugador",
        "confirmUrl", "https://ondgard.com/confirm?token=xyz"
    ));

    assertThat(result).contains("Bienvenido");
  }

  @Test
  void render_alreadyRegisteredEs_containsSpanishText() {
    String result = service.render("es", "already-registered", Map.of(
        "username", "Jugador",
        "loginUrl", "https://ondgard.com/login"
    ));

    assertThat(result).contains("Intento de registro");
  }

  @Test
  void render_passwordResetEn_replacesPlaceholders() {
    String result = service.render("en", "password-reset", Map.of(
        "username", "ResetUser",
        "resetUrl", "https://ondgard.com/reset-password?token=abc123"
    ));

    assertThat(result).contains("ResetUser");
    assertThat(result).contains("https://ondgard.com/reset-password?token=abc123");
    assertThat(result).contains("Password Reset");
    assertThat(result).doesNotContain("{{");
  }

  @Test
  void render_passwordResetIt_containsItalianText() {
    String result = service.render("it", "password-reset", Map.of(
        "username", "Giocatore",
        "resetUrl", "https://ondgard.com/reset-password?token=xyz"
    ));

    assertThat(result).contains("Reimpostazione Password");
  }

  @Test
  void render_passwordResetEs_containsSpanishText() {
    String result = service.render("es", "password-reset", Map.of(
        "username", "Jugador",
        "resetUrl", "https://ondgard.com/reset-password?token=xyz"
    ));

    assertThat(result).contains("Restablecimiento de Contrase");
  }

  @Test
  void getAvailableLanguages_returnsDiscoveredLanguages() {
    assertThat(service.getAvailableLanguages()).containsExactlyInAnyOrder("en", "it", "es");
  }

  @Test
  void render_unknownLanguageFallsBackToEnglish() {
    String result = service.render("fr", "confirmation", Map.of(
        "username", "Someone",
        "confirmUrl", "https://ondgard.com/confirm"
    ));

    assertThat(result).contains("Welcome to Ondgard!");
  }

  @Test
  void render_unknownTemplateThrowsIllegalArgument() {
    assertThatThrownBy(() -> service.render("en", "nonexistent", Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void render_allTemplatesAreValidHtml() {
    List<String> languages = List.of("en", "it", "es");
    List<String> templateNames = List.of("confirmation", "already-registered", "password-reset");

    Map<String, Map<String, String>> placeholders = Map.of(
        "confirmation", Map.of("username", "Test", "confirmUrl", "https://example.com"),
        "already-registered", Map.of("username", "Test", "loginUrl", "https://example.com"),
        "password-reset", Map.of("username", "Test", "resetUrl", "https://example.com")
    );

    for( String lang : languages ){
      for( String name : templateNames ){
        String result = service.render(lang, name, placeholders.get(name));
        assertThat(result.trim()).startsWith("<!DOCTYPE html>");
        assertThat(result.trim()).endsWith("</html>");
      }
    }
  }
}
