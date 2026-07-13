package com.ondgard.game.chat.service;

import com.ondgard.game.chat.client.AccountClient;
import com.ondgard.game.chat.config.ai.TokenTrackingContext;
import com.ondgard.game.chat.contract.ChatInteractionRequest;
import com.ondgard.game.chat.error.GameErrorCode;
import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.InteractionContext;
import com.ondgard.game.chat.model.PlayerCharacter;
import com.ondgard.game.chat.util.LanguageUtils;
import com.ondgard.game.exception.BadRequestException;
import com.ondgard.game.exception.TokenLimitExceededException;
import com.ondgard.game.header.GameUserHeader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Slf4j
@Service
@RequestScope
@RequiredArgsConstructor
public class ChatInteractionService {

  private final AccountClient accountClient;
  private final CharacterService characterService;
  private final CampaignService campaignService;
  private final LoreProvider loreProvider;
  private final GameMasterService gameMasterService;
  private final AdvisorService advisorService;

  public SseEmitter interact(GameUserHeader userHeader, ChatInteractionRequest request) {

    if( request.characterHash() == null || request.characterHash().isBlank() ){
      throw new BadRequestException(
          GameErrorCode.MISSING_CHARACTER_HASH.getCode(), "characterHash is required");
    }

    var limitCheck = accountClient.checkLimit(userHeader.getUserId());
    if( limitCheck.exceeded() ){
      throw new TokenLimitExceededException(GameErrorCode.TOKEN_LIMIT_EXCEEDED.getCode(), limitCheck);
    }

    final PlayerCharacter character = characterService.getCharacter(userHeader, request.characterHash());
    final CampaignContext campaignContext = campaignService.load(UUID.fromString(userHeader.getUserId()), request.characterHash());

    final String outputLang = LanguageUtils.toDisplayName(userHeader.getLanguage());
    final String loreLangCode = loreProvider.resolveLanguage(userHeader.getLanguage());

    TokenTrackingContext.set(request.characterHash());
    try{
      InteractionContext ctx = InteractionContext.builder()
          .userMessage(request.message())
          .character(character)
          .campaignContext(campaignContext)
          .outputLangIsoCode(userHeader.getLanguage())
          .outputLang(outputLang)
          .loreLangCode(loreLangCode)
          .build();

      return switch(request.mode()){
        case ACTION -> gameMasterService.processAction(userHeader, ctx);
        case ASK -> advisorService.processAsk(userHeader, ctx);
      };
    }finally{
      TokenTrackingContext.clear();
    }
  }
}
