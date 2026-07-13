package com.ondgard.game.chat.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.agent.ReviewerResponse;
import com.ondgard.game.chat.model.inventory.Inventory;
import com.ondgard.game.postprocessing.LlmRequired;
import com.ondgard.game.postprocessing.OndgardLlmSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.Collection;
import java.util.List;

@Slf4j
final class InventoryReviewerAgent implements LoreReviewer {

  private static final String TOPIC = "INVENTARIO";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int HISTORY_CONTEXT_SIZE = 3;

  private final ChatModel model;
  private final String systemPrompt;

  InventoryReviewerAgent(ChatModel model, String promptTemplate, Inventory inventory, Collection<ChatEntry> recentHistory) {
    this.model = model;
    this.systemPrompt = promptTemplate
        .replace("{{inventory}}", serializeInventory(inventory))
        .replace("{{recentHistory}}", serializeHistory(recentHistory));
  }

  @Override
  public ReviewerResponse review(String draft) {
    var converter = new BeanOutputConverter<>(InventoryReviewerLlmResponse.class);

    try{
      log.debug("InventoryReviewer: reviewing draft ({} chars)", draft.length());

      String response = ChatClient.create(model)
          .prompt()
          .system(systemPrompt)
          .user(draft)
          .call()
          .content();

      InventoryReviewerLlmResponse result = converter.convert(response);
      if( !result.isPass() ){
        log.debug("InventoryReviewer: isPass=false, because: {}", result.feedbackReason());
      }
      return new ReviewerResponse(result.isPass(), TOPIC, result.feedbackReason());
    }catch(Exception ex){
      log.error("InventoryReviewer: parsing/call failed — fail-open. Error: {}", ex.getMessage());
      return new ReviewerResponse(true, TOPIC, "Parsing error — fail-open");
    }
  }

  @Override
  public String topic() {
    return TOPIC;
  }

  private static String serializeInventory(Inventory inventory) {
    try{
      return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(inventory);
    }catch(Exception ex){
      log.error("Failed to serialize inventory", ex);
      return "{}";
    }
  }

  private static String serializeHistory(Collection<ChatEntry> recentHistory) {
    if( recentHistory == null || recentHistory.isEmpty() ){
      return "Nessun turno precedente.";
    }

    List<ChatEntry> entries = recentHistory instanceof List<ChatEntry> list ? list
        : List.copyOf(recentHistory);

    int fromIndex = Math.max(0, entries.size() - HISTORY_CONTEXT_SIZE);
    List<ChatEntry> lastEntries = entries.subList(fromIndex, entries.size());

    var sb = new StringBuilder();
    for( ChatEntry entry : lastEntries ){
      sb.append("### Turno ").append(entry.turnNumber()).append('\n');
      sb.append("**Giocatore:** ").append(entry.userMessage()).append('\n');
      sb.append("**Game Master:** ").append(entry.gmResponse()).append('\n');
      sb.append('\n');
    }
    return sb.toString().stripTrailing();
  }

  @OndgardLlmSchema( "INVENTORY_REVIEWER" )
  private static record InventoryReviewerLlmResponse(
      @LlmRequired boolean isPass,
      @LlmRequired String feedbackReason
  ) {
  }
}
