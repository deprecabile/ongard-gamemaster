package com.ondgard.game.tool;

import com.ondgard.game.model.validation.Message;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ComparatorBuilderTest {

  @Test void compareFirstField() {
    final Collection<Function<Message, String>> list = List.of(Message::getCode, Message::getMessage);
    Comparator<Message> comparator = ComparatorBuilder.buildComparator(String::compareTo, list);

    final Message left = Message.builder()
        .code("2")
        .message("BBB")
        .build();

    final Message right = Message.builder()
        .code("1")
        .message("BBB")
        .build();

    int compare = comparator.compare(left, right);
    assertThat(compare).isEqualTo(1);
  }

  @Test void compareOtherFields() {
    final Collection<Function<Message, String>> list = List.of(Message::getCode, Message::getMessage);
    Comparator<Message> comparator = ComparatorBuilder.buildComparator(String::compareTo, list);

    final Message left = Message.builder()
        .code("2")
        .message("BBB")
        .build();

    final Message right = Message.builder()
        .code("2")
        .message("AAA")
        .build();

    int compare = comparator.compare(left, right);
    assertThat(compare).isEqualTo(1);
  }

  @Test void compareEquals() {
    final Collection<Function<Message, String>> list = List.of(Message::getCode, Message::getMessage);
    Comparator<Message> comparator = ComparatorBuilder.buildComparator(String::compareTo, list);

    final Message left = Message.builder()
        .code("0")
        .message("AAA")
        .build();

    final Message right = Message.builder()
        .code("0")
        .message("AAA")
        .build();

    int compare = comparator.compare(left, right);
    assertThat(compare).isEqualTo(0);
  }

  @Test void handleNull() {
    final Collection<Function<Message, String>> list = List.of(Message::getCode, Message::getMessage);
    Comparator<Message> comparator = ComparatorBuilder.buildComparator(String::compareTo, list);

    final Message left = Message.builder()
        .code("2")
        .message("BBB")
        .build();

    final Message right = Message.builder()
        .code(null)
        .message("AAA")
        .build();

    int compare = comparator.compare(left, right);
    assertThat(compare).isEqualTo(-1);
  }

  @Test void handleNull_leftIsNull_shouldReturnPositive() {
    final Collection<Function<Message, String>> list = List.of(Message::getCode);
    Comparator<Message> comparator = ComparatorBuilder.buildComparator(String::compareTo, list);

    final Message left = Message.builder().code(null).build();
    final Message right = Message.builder().code("1").build();

    int compare = comparator.compare(left, right);
    assertThat(compare).isEqualTo(1); // null "greater" (sorted last)
  }

  @Test void handleNull_bothNull_shouldReturnZero() {
    final Collection<Function<Message, String>> list = List.of(Message::getCode);
    Comparator<Message> comparator = ComparatorBuilder.buildComparator(String::compareTo, list);

    final Message left = Message.builder().code(null).build();
    final Message right = Message.builder().code(null).build();

    int compare = comparator.compare(left, right);
    assertThat(compare).isEqualTo(0);
  }

  @Test void varargsOverload_shouldBehaveLikeCollectionVersion() {
    Comparator<Message> comparator = ComparatorBuilder.buildComparator(
        String::compareTo, Message::getCode, Message::getMessage);

    final Message left = Message.builder().code("1").message("BBB").build();
    final Message right = Message.builder().code("1").message("AAA").build();

    int compare = comparator.compare(left, right);
    assertThat(compare).isEqualTo(1); // fallback sul secondo campo
  }

  @Test void emptyGetterList_shouldAlwaysReturnZero() {
    Comparator<Message> comparator = ComparatorBuilder.buildComparator(String::compareTo, List.of());

    final Message left = Message.builder().code("Z").message("ZZZ").build();
    final Message right = Message.builder().code("A").message("AAA").build();

    int compare = comparator.compare(left, right);
    assertThat(compare).isEqualTo(0);
  }
}