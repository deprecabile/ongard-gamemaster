package com.ondgard.game.chat.util;

import com.ondgard.game.chat.model.ChatEntry;

import java.util.List;

public final class ChatEntryFormatter {

  private ChatEntryFormatter() {
  }

  public static String toTextBuffer(List<ChatEntry> entries) {
    if( entries == null || entries.isEmpty() ){
      return "";
    }
    var sb = new StringBuilder();
    for( var e : entries ){
      sb.append("Turno ").append(e.turnNumber()).append(":\n")
          .append("- Giocatore: ").append(e.userMessage()).append("\n")
          .append("- GM: ").append(e.gmResponse()).append("\n\n");
    }
    return sb.toString();
  }
}
