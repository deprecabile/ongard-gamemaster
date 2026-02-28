package com.ongard.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class GameGsonFactory {

  private GameGsonFactory() {
  }

  public static Gson build() {
    return new GsonBuilder().setDateFormat("yyyy-MM-dd")
        .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
        .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
        .create();
  }

  // ************************************ private ************************************


  private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
    @Override public void write(JsonWriter jsonWriter, LocalDateTime date) throws IOException {
      if( date == null ){
        jsonWriter.nullValue();
      } else{
        jsonWriter.value(date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
      }
    }

    @Override public LocalDateTime read(JsonReader jsonReader) throws IOException {
      if( jsonReader.peek() == JsonToken.NULL ){
        jsonReader.nextNull();
        return null;
      } else{
        return LocalDateTime.parse(jsonReader.nextString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
      }
    }
  }

  private static class LocalDateAdapter extends TypeAdapter<LocalDate> {

    @Override public void write(JsonWriter jsonWriter, LocalDate localDate) throws IOException {
      if( localDate == null ){
        jsonWriter.nullValue();
      } else{
        jsonWriter.value(localDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
      }
    }

    @Override public LocalDate read(JsonReader jsonReader) throws IOException {
      if( jsonReader.peek() == JsonToken.NULL ){
        jsonReader.nextNull();
        return null;
      } else{
        return LocalDate.parse(jsonReader.nextString(), DateTimeFormatter.ISO_LOCAL_DATE);
      }
    }
  }

}
