package com.ongard.game.chat.config;

import com.google.gson.Gson;
import com.ongard.game.GameGsonFactory;
import com.ongard.game.exception.BadRequestException;
import com.ongard.game.header.GameUserHeader;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts a JSON string from the header into a GameUserHeader object.
 */
@Log4j2
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
      log.warn(e);
      throw new BadRequestException("Invalid GameUserHeader JSON: " + source);
    }
  }
}
