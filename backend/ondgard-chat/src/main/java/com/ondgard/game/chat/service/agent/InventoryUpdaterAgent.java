package com.ondgard.game.chat.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondgard.game.chat.model.inventory.Inventory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;

@Slf4j
final class InventoryUpdaterAgent {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ChatModel model;
  private final String systemPrompt;
  private final Inventory currentInventory;
  private final String userAction;

  InventoryUpdaterAgent(ChatModel model, String promptTemplate, Inventory inventory, String userAction) {
    this.model = model;
    this.currentInventory = inventory;
    this.userAction = userAction;
    this.systemPrompt = promptTemplate.replace("{{inventory}}", serializeInventory(inventory));
  }

  public Inventory update(String approvedDraft) {
    var converter = new BeanOutputConverter<>(Inventory.class);

    try{
      log.debug("InventoryUpdater: updating inventory from draft ({} chars)", approvedDraft.length());

      String userMessage = buildUserMessage(approvedDraft);

      String response = ChatClient.create(model)
          .prompt()
          .system(systemPrompt)
          .user(userMessage)
          .call()
          .content();

      Inventory updated = converter.convert(response);
      log.debug("InventoryUpdater: inventory updated successfully");
      return updated;
    }catch(Exception ex){
      log.warn("InventoryUpdater: parsing/call failed — returning current inventory. Error: {}", ex.getMessage());
      return currentInventory;
    }
  }

  private String buildUserMessage(String approvedDraft) {
    if( userAction == null || userAction.isBlank() ){
      return approvedDraft;
    }
    return "## Narrazione del GM\n\n" + approvedDraft
        + "\n\n## Azione dichiarata dal giocatore\n\n" + userAction;
  }

  private static String serializeInventory(Inventory inventory) {
    try{
      return MAPPER.writeValueAsString(inventory);
    }catch(Exception ex){
      log.error("Failed to serialize inventory", ex);
      return "{}";
    }
  }
}
