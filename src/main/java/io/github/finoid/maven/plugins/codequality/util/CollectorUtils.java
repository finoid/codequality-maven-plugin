package io.github.finoid.maven.plugins.codequality.util;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@UtilityClass
public class CollectorUtils {
    /**
     * Alternative to {@link Collectors#toList} which returns an unmodifiable list.
     *
     * @param <T> the type of the elements
     * @return modifiable list
     */
    public static <T> Collector<T, ?, List<T>> toMutableList() {
        return Collectors.toCollection(ArrayList::new);
    }
}
