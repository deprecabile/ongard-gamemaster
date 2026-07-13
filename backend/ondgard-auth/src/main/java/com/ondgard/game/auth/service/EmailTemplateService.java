package com.ondgard.game.auth.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class EmailTemplateService {

  private static final String DEFAULT_LANG = "en";
  private static final List<String> TEMPLATE_NAMES = List.of("confirmation", "already-registered", "password-reset");
  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");


  private Map<String, Map<String, String>> templates;
  @Getter private Collection<String> availableLanguages;

  @PostConstruct
  void loadTemplates() throws IOException {
    var resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources = resolver.getResources("classpath:templates/*/" + TEMPLATE_NAMES.getFirst() + ".html");

    var mutableTemplates = new HashMap<String, Map<String, String>>();
    for( Resource resource : resources ){
      String[] segments = resource.getURL().getPath().split("/");
      String lang = segments[segments.length - 2];

      final Map<String, String> langTemplates = new HashMap<String, String>();
      for( String name : TEMPLATE_NAMES ){
        String path = "templates/" + lang + "/" + name + ".html";
        String html = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        langTemplates.put(name, html);
        log.debug("Loaded email template: {}/{}", lang, name);
      }
      mutableTemplates.put(lang, Map.copyOf(langTemplates));
    }

    if( !mutableTemplates.containsKey(DEFAULT_LANG) ){
      throw new IllegalStateException("Default language '" + DEFAULT_LANG + "' templates not found");
    }

    templates = Map.copyOf(mutableTemplates);
    availableLanguages = Set.copyOf(templates.keySet());
    log.info("Email templates loaded for languages: {}", availableLanguages);
  }

  public String render(String lang, String templateName, Map<String, String> placeholders) {
    String resolvedLang = templates.containsKey(lang) ? lang : DEFAULT_LANG;
    Map<String, String> langTemplates = templates.get(resolvedLang);

    if( !langTemplates.containsKey(templateName) ){
      throw new IllegalArgumentException("Unknown email template: " + templateName);
    }

    String html = langTemplates.get(templateName);
    return PLACEHOLDER_PATTERN.matcher(html).replaceAll(match -> {
      String value = placeholders.get(match.group(1));
      return value != null ? Matcher.quoteReplacement(value) : match.group();
    });
  }
}
