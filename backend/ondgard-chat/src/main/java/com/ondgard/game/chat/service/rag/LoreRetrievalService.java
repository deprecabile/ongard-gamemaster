package com.ondgard.game.chat.service.rag;

import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.GameScene;

public interface LoreRetrievalService {

  String retrieve(String loreLangCode, String userAction, GameScene scene, String questActive, ChatEntry lastTurn);

  String retrieveForAdvisor(String loreLangCode, String query);
}
