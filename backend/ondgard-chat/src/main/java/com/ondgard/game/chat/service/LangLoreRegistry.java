package com.ondgard.game.chat.service;

import lombok.Getter;

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

final class LangLoreRegistry implements LoreRegistry {

  @Getter private final String language;
  private final Map<String, LoreCategory> categories;
  private final String allLoreConcatenated;

  public LangLoreRegistry(String language, TreeMap<String, TreeMap<String, String>> categoryFiles) {
    this.language = language;
    this.categories = new TreeMap<>();
    StringBuilder fullLore = new StringBuilder();

    for( var entry : categoryFiles.entrySet() ){
      String categoryKey = entry.getKey();
      SortedMap<String, String> files = entry.getValue();

      StringBuilder categoryContent = new StringBuilder();
      for( String fileContent : files.values() ){
        if( !categoryContent.isEmpty() ){
          categoryContent.append("\n\n");
        }
        categoryContent.append(fileContent);
      }

      categories.put(categoryKey, new LoreCategory(categoryKey, categoryContent.toString(), files.size()));

      if( !fullLore.isEmpty() ){
        fullLore.append("\n\n---\n\n");
      }
      fullLore.append("# ").append(categoryKey).append("\n\n");
      fullLore.append(categoryContent);
    }

    allLoreConcatenated = fullLore.toString();
  }

  @Override
  public String getLore(String categoryKey) {
    var category = categories.get(categoryKey.toUpperCase());
    return category != null ? category.content() : "";
  }

  @Override
  public String getAllLore() {
    return allLoreConcatenated;
  }

  @Override
  public Set<String> getCategoryKeys() {
    return categories.keySet();
  }

  private record LoreCategory( String key, String content, int fileCount ) {
  }
}
