package com.ondgard.game.chat.service;

import com.ondgard.game.chat.client.AccountClient;
import com.ondgard.game.chat.config.ai.TokenTrackingContext;
import com.ondgard.game.chat.contract.ChatInteractionRequest;
import com.ondgard.game.chat.contract.ChatMode;
import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.InteractionContext;
import com.ondgard.game.chat.model.PlayerCharacter;
import com.ondgard.game.contract.account.CheckLimitResponse;
import com.ondgard.game.exception.BadRequestException;
import com.ondgard.game.exception.TokenLimitExceededException;
import com.ondgard.game.header.GameUserHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith( MockitoExtension.class )
class ChatInteractionServiceTest {

  @Mock private AccountClient accountClient;
  @Mock private CharacterService characterService;
  @Mock private CampaignService campaignService;
  @Mock private LoreProvider loreProvider;
  @Mock private GameMasterService gameMasterService;
  @Mock private AdvisorService advisorService;

  private ChatInteractionService chatInteractionService;

  private static final String USER_ID = "997a7552-5c1b-42c6-a0c0-094c50a0ad53";
  private static final String CHARACTER_HASH = "abc123def456";

  private static final GameUserHeader USER_HEADER = GameUserHeader.builder()
      .userId(USER_ID)
      .username("testuser")
      .language("en")
      .build();

  @BeforeEach
  void setUp() {
    chatInteractionService = new ChatInteractionService(
        accountClient, characterService, campaignService, loreProvider, gameMasterService, advisorService);
  }

  @AfterEach
  void cleanup() {
    TokenTrackingContext.clear();
  }

  // ---- Validation ----

  @Test
  void interact_nullCharacterHash_throwsBadRequest() {
    var request = new ChatInteractionRequest(null, ChatMode.ACTION, "hello");

    assertThatThrownBy(() -> chatInteractionService.interact(USER_HEADER, request))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void interact_blankCharacterHash_throwsBadRequest() {
    var request = new ChatInteractionRequest("   ", ChatMode.ACTION, "hello");

    assertThatThrownBy(() -> chatInteractionService.interact(USER_HEADER, request))
        .isInstanceOf(BadRequestException.class);
  }

  // ---- Token limit ----

  @Test
  void interact_tokenLimitExceeded_throwsTokenLimitExceededException() {
    var request = new ChatInteractionRequest(CHARACTER_HASH, ChatMode.ACTION, "hello");
    when(accountClient.checkLimit(USER_ID)).thenReturn(new CheckLimitResponse(true, "MONTHLY"));

    assertThatThrownBy(() -> chatInteractionService.interact(USER_HEADER, request))
        .isInstanceOf(TokenLimitExceededException.class)
        .hasMessageContaining("MONTHLY");
  }

  // ---- ACTION dispatch ----

  @Test
  void interact_actionMode_delegatesToGameMasterService() {
    var request = new ChatInteractionRequest(CHARACTER_HASH, ChatMode.ACTION, "I attack the dragon");
    var expectedEmitter = new SseEmitter();

    var character = PlayerCharacter.builder().characterHash(CHARACTER_HASH).name("Gimli").build();
    var campaignContext = CampaignContext.builder().currentTurn(1).build();

    when(accountClient.checkLimit(USER_ID)).thenReturn(new CheckLimitResponse(false, null));
    when(characterService.getCharacter(USER_HEADER, CHARACTER_HASH)).thenReturn(character);
    when(campaignService.load(UUID.fromString(USER_ID), CHARACTER_HASH)).thenReturn(campaignContext);
    when(loreProvider.resolveLanguage("en")).thenReturn("en");
    when(gameMasterService.processAction(eq(USER_HEADER), any(InteractionContext.class))).thenReturn(expectedEmitter);

    SseEmitter result = chatInteractionService.interact(USER_HEADER, request);

    assertThat(result).isSameAs(expectedEmitter);
    verify(gameMasterService).processAction(eq(USER_HEADER), any(InteractionContext.class));
  }

  @Test
  void interact_actionMode_buildsCorrectInteractionContext() {
    var request = new ChatInteractionRequest(CHARACTER_HASH, ChatMode.ACTION, "I open the chest");

    var character = PlayerCharacter.builder().characterHash(CHARACTER_HASH).name("Legolas").build();
    var campaignContext = CampaignContext.builder().currentTurn(5).build();

    when(accountClient.checkLimit(USER_ID)).thenReturn(new CheckLimitResponse(false, null));
    when(characterService.getCharacter(USER_HEADER, CHARACTER_HASH)).thenReturn(character);
    when(campaignService.load(UUID.fromString(USER_ID), CHARACTER_HASH)).thenReturn(campaignContext);
    when(loreProvider.resolveLanguage("en")).thenReturn("en");
    when(gameMasterService.processAction(eq(USER_HEADER), any(InteractionContext.class))).thenReturn(new SseEmitter());

    chatInteractionService.interact(USER_HEADER, request);

    ArgumentCaptor<InteractionContext> captor = ArgumentCaptor.forClass(InteractionContext.class);
    verify(gameMasterService).processAction(eq(USER_HEADER), captor.capture());

    InteractionContext ctx = captor.getValue();
    assertThat(ctx.getUserMessage()).isEqualTo("I open the chest");
    assertThat(ctx.getCharacter()).isSameAs(character);
    assertThat(ctx.getCampaignContext()).isSameAs(campaignContext);
    assertThat(ctx.getOutputLangIsoCode()).isEqualTo("en");
    assertThat(ctx.getLoreLangCode()).isEqualTo("en");
    assertThat(ctx.getOutputLang()).isNotBlank();
  }

  // ---- TokenTrackingContext lifecycle ----

  @Test
  void interact_actionMode_clearsTokenTrackingContextAfterDispatch() {
    var request = new ChatInteractionRequest(CHARACTER_HASH, ChatMode.ACTION, "hello");

    var character = PlayerCharacter.builder().characterHash(CHARACTER_HASH).build();
    var campaignContext = CampaignContext.builder().build();

    when(accountClient.checkLimit(USER_ID)).thenReturn(new CheckLimitResponse(false, null));
    when(characterService.getCharacter(USER_HEADER, CHARACTER_HASH)).thenReturn(character);
    when(campaignService.load(UUID.fromString(USER_ID), CHARACTER_HASH)).thenReturn(campaignContext);
    when(loreProvider.resolveLanguage("en")).thenReturn("en");
    when(gameMasterService.processAction(eq(USER_HEADER), any(InteractionContext.class))).thenReturn(new SseEmitter());

    chatInteractionService.interact(USER_HEADER, request);

    assertThat(TokenTrackingContext.get()).isNull();
  }

  @Test
  void interact_clearsTokenTrackingContextOnException() {
    var request = new ChatInteractionRequest(CHARACTER_HASH, ChatMode.ACTION, "hello");

    var character = PlayerCharacter.builder().characterHash(CHARACTER_HASH).build();
    var campaignContext = CampaignContext.builder().build();

    when(accountClient.checkLimit(USER_ID)).thenReturn(new CheckLimitResponse(false, null));
    when(characterService.getCharacter(USER_HEADER, CHARACTER_HASH)).thenReturn(character);
    when(campaignService.load(UUID.fromString(USER_ID), CHARACTER_HASH)).thenReturn(campaignContext);
    when(loreProvider.resolveLanguage("en")).thenReturn("en");
    when(gameMasterService.processAction(eq(USER_HEADER), any(InteractionContext.class)))
        .thenThrow(new RuntimeException("pipeline error"));

    try{
      chatInteractionService.interact(USER_HEADER, request);
    }catch(RuntimeException ignored){
    }

    assertThat(TokenTrackingContext.get()).isNull();
  }

  // ---- ASK dispatch ----

  @Test
  void interact_askMode_delegatesToAdvisorService() {
    var request = new ChatInteractionRequest(CHARACTER_HASH, ChatMode.ASK, "Chi sono i nani?");
    var expectedEmitter = new SseEmitter();

    var character = PlayerCharacter.builder().characterHash(CHARACTER_HASH).name("Gimli").build();
    var campaignContext = CampaignContext.builder().currentTurn(1).build();

    when(accountClient.checkLimit(USER_ID)).thenReturn(new CheckLimitResponse(false, null));
    when(characterService.getCharacter(USER_HEADER, CHARACTER_HASH)).thenReturn(character);
    when(campaignService.load(UUID.fromString(USER_ID), CHARACTER_HASH)).thenReturn(campaignContext);
    when(loreProvider.resolveLanguage("en")).thenReturn("en");
    when(advisorService.processAsk(eq(USER_HEADER), any(InteractionContext.class)))
        .thenReturn(expectedEmitter);

    SseEmitter result = chatInteractionService.interact(USER_HEADER, request);

    assertThat(result).isSameAs(expectedEmitter);
    verify(advisorService).processAsk(eq(USER_HEADER), any(InteractionContext.class));
  }

  // ---- TokenTrackingContext lifecycle ----

  @Test
  void interact_actionMode_setsTokenTrackingContextDuringDispatch() {
    var request = new ChatInteractionRequest(CHARACTER_HASH, ChatMode.ACTION, "hello");

    var character = PlayerCharacter.builder().characterHash(CHARACTER_HASH).build();
    var campaignContext = CampaignContext.builder().build();

    when(accountClient.checkLimit(USER_ID)).thenReturn(new CheckLimitResponse(false, null));
    when(characterService.getCharacter(USER_HEADER, CHARACTER_HASH)).thenReturn(character);
    when(campaignService.load(UUID.fromString(USER_ID), CHARACTER_HASH)).thenReturn(campaignContext);
    when(loreProvider.resolveLanguage("en")).thenReturn("en");
    when(gameMasterService.processAction(eq(USER_HEADER), any(InteractionContext.class)))
        .thenAnswer(invocation -> {
          assertThat(TokenTrackingContext.get()).isEqualTo(CHARACTER_HASH);
          return new SseEmitter();
        });

    chatInteractionService.interact(USER_HEADER, request);
  }
}
