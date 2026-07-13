package com.ondgard.game.chat.config;

import com.google.gson.Gson;
import com.ondgard.game.GameGsonFactory;
import com.ondgard.game.exception.BadRequestException;
import com.ondgard.game.header.GameUserHeader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts a JSON string from the header into a GameUserHeader object.
 */
@Slf4j
@Component
public class GameUserHeaderConverter implements Converter<String, GameUserHeader> {

  private final Gson gson = GameGsonFactory.build();

  @Override
  public GameUserHeader convert(String source) {
    if( source == null || source.isBlank() ){
      throw new BadRequestException("GameUserHeader cannot be empty");
    }
    try{
      return gson.fromJson(source, GameUserHeader.class);
    }catch(Exception e){
      final String msg = "Invalid GameUserHeader JSON: " + source;
      log.warn(msg, e);
      throw new BadRequestException(msg);
    }
  }
}
