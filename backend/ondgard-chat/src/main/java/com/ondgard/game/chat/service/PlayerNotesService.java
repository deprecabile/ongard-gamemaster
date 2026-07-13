package com.ondgard.game.chat.service;

import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.entity.CampaignNotesEntity;
import com.ondgard.game.chat.repository.CampaignNotesRepository;
import com.ondgard.game.chat.repository.CampaignRepository;
import com.ondgard.game.exception.NoResultException;
import com.ondgard.game.header.GameUserHeader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlayerNotesService {

  private final CampaignRepository campaignRepository;
  private final CampaignNotesRepository campaignNotesRepository;

  public String getPlayerNotes(GameUserHeader userHeader, String characterHash) {
    final CampaignEntity entity = findOwnedCampaign(userHeader, characterHash);
    return campaignNotesRepository.findByCampaignId(entity.getCharacterId())
        .map(CampaignNotesEntity::getContent)
        .orElse("");
  }

  @Transactional
  public void updatePlayerNotes(GameUserHeader userHeader, String characterHash, String content) {
    CampaignEntity entity = findOwnedCampaign(userHeader, characterHash);
    campaignNotesRepository.updateContent(entity.getCharacterId(), content);
  }

  private CampaignEntity findOwnedCampaign(GameUserHeader userHeader, String characterHash) {
    final UUID userHash = UUID.fromString(userHeader.getUserId());
    return campaignRepository.findByCharacterHashAndUserHash(userHash, characterHash)
        .orElseThrow(NoResultException::new);
  }
}
