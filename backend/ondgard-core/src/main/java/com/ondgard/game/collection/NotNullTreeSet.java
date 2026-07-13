package com.ondgard.game.collection;

import java.io.Serializable;
import java.util.*;

public class NotNullTreeSet<T> extends TreeSet<T> {

  public interface NullRule<K> extends Serializable {
    boolean isNull(K item);
  }

  private final NullRule<T> rule;

  public NotNullTreeSet(Comparator<T> comparator) {
    this(comparator, Objects::isNull);
  }

  public NotNullTreeSet(Comparator<T> comparator, NullRule<T> rule) {
    super(comparator);
    this.rule = rule;
  }

  @Override public boolean add(T t) {
    if( rule.isNull(t) ){
      return false;
    }
    return super.add(t);
  }

  @Override public boolean equals(Object o) {
    return super.equals(o);
  }

  @Override public int hashCode() {
    return super.hashCode();
  }

  public static <E> Builder<E> builder(Comparator<E> comparator) {
    return new Builder<>(comparator);
  }

  public static final class Builder<E> {
    private final Comparator<E> comparator;
    private Collection<E> elements = new ArrayList<>();
    private NullRule<E> rule = Objects::isNull;

    private Builder(Comparator<E> comparator) {
      this.comparator = comparator;
    }

    public Builder<E> withRule(NullRule<E> rule) {
      this.rule = rule;
      return this;
    }

    @SafeVarargs
    public final Builder<E> of(E... items) {
      this.elements = Arrays.asList(items);
      return this;
    }

    public Builder<E> of(Collection<E> items) {
      this.elements = new ArrayList<>(items);
      return this;
    }

    public NotNullTreeSet<E> build() {
      NotNullTreeSet<E> set = new NotNullTreeSet<>(comparator, rule);
      set.addAll(elements);
      return set;
    }
  }
}
