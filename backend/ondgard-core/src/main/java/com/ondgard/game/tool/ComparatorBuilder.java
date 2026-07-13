package com.ondgard.game.tool;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.Function;

@NoArgsConstructor( access = AccessLevel.PRIVATE )
public class ComparatorBuilder {

  @SafeVarargs
  public static <K, T> Comparator<K> buildComparator(Comparator<T> basicCompare, Function<K, T>... getters) {
    return buildComparator(basicCompare, Arrays.asList(getters));
  }

  public static <K, T> Comparator<K> buildComparator(Comparator<T> basicCompare, Collection<Function<K, T>> getterList) {
    return (K left, K right) -> {
      for( Function<K, T> getter : getterList ){
        final T fieldL = getter.apply(left);
        final T fieldR = getter.apply(right);
        final int compare = compareHandleNull(basicCompare, fieldL, fieldR);
        if( compare != 0 ){
          return compare;
        }
      }
      return 0;
    };
  }

  private static <T> int compareHandleNull(Comparator<T> basicCompare, T left, T right) {
    final int ret;
    if( left == null && right == null ){
      ret = 0;
    } else if( left == null ){
      ret = 1;
    } else if( right == null ){
      ret = -1;
    } else{
      ret = basicCompare.compare(left, right);
    }

    return ret;
  }
}
