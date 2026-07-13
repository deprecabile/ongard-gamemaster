package com.ondgard.game.chat.service;

import java.util.Set;

public interface LoreRegistry {

  String getLore(String categoryKey);

  String getAllLore();

  Set<String> getCategoryKeys();
}
