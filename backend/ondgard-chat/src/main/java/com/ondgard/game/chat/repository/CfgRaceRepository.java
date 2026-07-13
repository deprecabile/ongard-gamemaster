package com.ondgard.game.chat.repository;

import com.ondgard.game.chat.entity.CfgRaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CfgRaceRepository extends JpaRepository<CfgRaceEntity, Long> {
}
