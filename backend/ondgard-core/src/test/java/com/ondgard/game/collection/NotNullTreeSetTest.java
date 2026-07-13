package com.ondgard.game.collection;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class NotNullTreeSetTest {

  @Test
  void add_withDefaultRule_shouldRejectNullsAndAcceptNonNulls() {
    Set<String> set = new NotNullTreeSet<String>(Comparator.naturalOrder());

    boolean addedNull = set.add(null);
    boolean addedValid1 = set.add("b");
    boolean addedValid2 = set.add("a");
    boolean addedValid3 = set.add("a"); // Duplicate

    assertThat(addedNull).isFalse();
    assertThat(addedValid1).isTrue();
    assertThat(addedValid2).isTrue();
    assertThat(addedValid3).isFalse(); // TreeSet duplicate behavior

    assertThat(set).hasSize(2)
        .containsExactly("a", "b"); // Check sorting and elements
  }

  @Test
  void add_withCustomRule_shouldRejectElementsMatchingRule() {
    NotNullTreeSet.NullRule<String> emptyStringRule = item -> item == null || item.isEmpty();
    Set<String> set = new NotNullTreeSet<>(Comparator.naturalOrder(), emptyStringRule);

    boolean addedNull = set.add(null);
    boolean addedEmpty = set.add("");
    boolean addedValid1 = set.add("hello");
    boolean addedValid2 = set.add("world");

    assertThat(addedNull).isFalse();
    assertThat(addedEmpty).isFalse();
    assertThat(addedValid1).isTrue();
    assertThat(addedValid2).isTrue();

    assertThat(set).hasSize(2)
        .containsExactly("hello", "world");
  }

  @Test
  void equalsAndHashCode_shouldDelegateToSuper() {
    Collection<String> set1 = new NotNullTreeSet<String>(Comparator.naturalOrder());
    set1.add("a");
    set1.add("b");

    Collection<String> set2 = new NotNullTreeSet<String>(Comparator.naturalOrder());
    set2.add("a");
    set2.add("b");

    Collection<String> set3 = new NotNullTreeSet<String>(Comparator.naturalOrder());
    set3.add("c");

    assertThat(set1.equals(set2)).isTrue();
    assertThat(set1.equals(set3)).isFalse();
    assertThat(set1.hashCode()).isEqualTo(set2.hashCode());
    assertThat(set1.hashCode()).isNotEqualTo(set3.hashCode());
  }

  @Test
  void builder_shouldCreateSetWithElements() {
    Collection<String> set = NotNullTreeSet.<String>builder(Comparator.naturalOrder())
        .of("hello", null, "world", "world")
        .build();

    assertThat(set).hasSize(2)
        .containsExactly("hello", "world");
  }

  @Test
  void builder_withCustomRule_shouldCreateSetWithElements() {
    NotNullTreeSet.NullRule<String> emptyStringRule = item -> item == null || item.isEmpty();
    Collection<String> set = NotNullTreeSet.<String>builder(Comparator.naturalOrder())
        .withRule(emptyStringRule)
        .of("hello", "", "world", null)
        .build();

    assertThat(set).hasSize(2)
        .containsExactly("hello", "world");
  }

  @Test
  void addAll_withDefaultRule_shouldFilterNulls() {
    Collection<String> set = new NotNullTreeSet<String>(Comparator.naturalOrder());

    set.addAll(Arrays.asList("b", null, "a", null));

    assertThat(set).hasSize(2)
        .containsExactly("a", "b");
  }

  @Test
  void addAll_withCustomRule_shouldFilterMatchingElements() {
    NotNullTreeSet.NullRule<String> emptyStringRule = item -> item == null || item.isEmpty();
    Collection<String> set = new NotNullTreeSet<>(Comparator.naturalOrder(), emptyStringRule);

    set.addAll(Arrays.asList("hello", null, "", "world"));

    assertThat(set).hasSize(2)
        .containsExactly("hello", "world");
  }

  @Test
  void newSet_shouldBeEmpty() {
    Collection<String> set = new NotNullTreeSet<String>(Comparator.naturalOrder());

    assertThat(set).isEmpty();
    assertThat(set).hasSize(0);
  }

  @Test
  void builder_withoutOf_shouldCreateEmptySet() {
    Collection<String> set = NotNullTreeSet.<String>builder(Comparator.naturalOrder())
        .build();

    assertThat(set).isEmpty();
  }

  @Test
  void equals_withNullAndDifferentType_shouldReturnFalse() {
    Collection<String> set = new NotNullTreeSet<String>(Comparator.naturalOrder());
    set.add("a");

    assertThat(set == null).isFalse();
    assertThat(set.equals("stringa")).isFalse();
    assertThat(set.equals(List.of("a"))).isFalse();
  }

  @Test
  void add_withReverseComparator_shouldPreserveComparatorOrder() {
    Collection<String> set = new NotNullTreeSet<String>(Comparator.reverseOrder());
    set.add("a");
    set.add("c");
    set.add("b");

    assertThat(set).containsExactly("c", "b", "a");
  }

  @Test
  void builder_withCollectionOf_shouldCreateSetWithElements() {
    List<String> input = Arrays.asList("world", null, "hello", "world");

    Collection<String> set = NotNullTreeSet.<String>builder(Comparator.naturalOrder())
        .of(input)
        .build();

    assertThat(set).hasSize(2)
        .containsExactly("hello", "world");
  }
}
