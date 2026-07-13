package com.ondgard.game.chat.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.CRC32;

@Slf4j
@Service
@NoArgsConstructor
public class LoreProvider {

  private static final String LORE_PREFIX = "game_data/ondgard/";

  private SortedMap<String, LoreRegistry> loreByLanguage;
  @Getter private String contentHash;

  @PostConstruct
  void loadAllLore() throws IOException {
    var resolver = new PathMatchingResourcePatternResolver();
    Resource[] mdResources = resolver.getResources("classpath:game_data/ondgard/**/*.md");
    Resource[] txtResources = resolver.getResources("classpath:game_data/ondgard/**/*.txt");

    // lang → category → filename → content (TreeMap for deterministic ordering)
    var langCategoryFiles = new TreeMap<String, TreeMap<String, TreeMap<String, String>>>();

    for( Resource resource : concat(mdResources, txtResources) ){
      String relativePath = extractRelativePath(resource);
      if( relativePath == null ) continue;

      // Parse as {lang}/{category}/{filename}
      int firstSlash = relativePath.indexOf('/');
      int secondSlash = relativePath.indexOf('/', firstSlash + 1);
      if( firstSlash == -1 || secondSlash == -1 ){
        // File at lang root level (e.g. scene_rag_query.txt) → skip
        continue;
      }

      String lang = relativePath.substring(0, firstSlash);
      String categoryKey = relativePath.substring(firstSlash + 1, secondSlash).toUpperCase();
      String fileName = relativePath.substring(relativePath.lastIndexOf('/') + 1);

      String content = resource.getContentAsString(StandardCharsets.UTF_8).strip();
      if( content.isEmpty() ) continue;

      langCategoryFiles.computeIfAbsent(lang, k -> new TreeMap<>())
          .computeIfAbsent(categoryKey, k -> new TreeMap<>())
          .put(fileName, content);
    }

    // Build LangLoreRegistry per language
    loreByLanguage = new TreeMap<>();
    var allLoreForHash = new StringBuilder();

    for( var langEntry : langCategoryFiles.entrySet() ){
      String lang = langEntry.getKey();
      TreeMap<String, TreeMap<String, String>> categoryFiles = langEntry.getValue();

      // Remove categories with only empty files
      categoryFiles.values().removeIf(SortedMap::isEmpty);

      LoreRegistry registry = new LangLoreRegistry(lang, categoryFiles);
      loreByLanguage.put(lang, registry);

      if( !allLoreForHash.isEmpty() ){
        allLoreForHash.append("\n");
      }
      allLoreForHash.append(registry.getAllLore());

      log.info("LoreProvider [{}]: {} categories, {} total chars",
          lang, registry.getCategoryKeys().size(), registry.getAllLore().length());
    }

    contentHash = computeCrc32(allLoreForHash.toString());
    log.info("LoreProvider initialized: {} languages ({}), content hash: {}",
        loreByLanguage.size(), loreByLanguage.keySet(), contentHash);
  }

  public LoreRegistry get(String language) {
    return loreByLanguage.get(resolveLanguage(language));
  }

  public String resolveLanguage(String requested) {
    if( loreByLanguage.containsKey(requested) ) return requested;
    if( loreByLanguage.containsKey("en") ) return "en";
    return loreByLanguage.firstKey();
  }

  public Collection<String> getAvailableLanguages() {
    return loreByLanguage.keySet();
  }

  // ================================= private =================================

  private String extractRelativePath(Resource resource) throws IOException {
    String uri = resource.getURI().toString();
    int prefixIndex = uri.indexOf(LORE_PREFIX);
    if( prefixIndex == -1 ) return null;
    return uri.substring(prefixIndex + LORE_PREFIX.length());
  }

  private Resource[] concat(Resource[] a, Resource[] b) {
    var result = new Resource[a.length + b.length];
    System.arraycopy(a, 0, result, 0, a.length);
    System.arraycopy(b, 0, result, a.length, b.length);
    return result;
  }

  private static String computeCrc32(String input) {
    var crc = new CRC32();
    crc.update(input.getBytes(StandardCharsets.UTF_8));
    return Long.toHexString(crc.getValue());
  }
}
