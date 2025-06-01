package io.github.finoid.maven.plugins.codequality.fixtures.snapshot;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

/**
 * A custom Jackson module that ensures collections are serialized in a **deterministic order**.
 * This module replaces the default Jackson serialization of {@link Collection} types
 * by converting them into sorted arrays before serialization. This helps create
 * consistent JSON snapshots, preventing variations due to unordered collections.
 * <p>
 * If sorting is not possible (e.g., due to non-comparable elements), the collection
 * will be serialized in its original order, and a debug log will be emitted.
 */
@Slf4j
public class DeterministicCollectionModule extends SimpleModule {
    @SuppressWarnings("this-escape")
    public DeterministicCollectionModule() {
        addSerializer(Collection.class, new CollectionSerializer());
    }

    /**
     * Collections gets converted into a sorted Object[].  This then gets serialized using the default Array serializer.
     */
    @SuppressWarnings("rawtypes")
    private static class CollectionSerializer extends JsonSerializer<Collection> {
        /**
         * Serializes a {@link Collection} into a sorted {@code Object[]} for deterministic output.
         *
         * @param value       the collection to be serialized
         * @param gen         the JSON generator
         * @param serializers the serializer provider
         * @throws IOException if an error occurs during serialization
         */
        @Override
        @SuppressWarnings("rawtypes")
        public void serialize(final Collection value, final JsonGenerator gen, final SerializerProvider serializers) throws IOException {
            final Object[] sorted = convert(value);

            serializers.defaultSerializeValue(sorted, gen);
        }

        /**
         * Converts a collection into a sorted array, or an unsorted array if sorting fails.
         *
         * @param value the collection to be converted
         * @return a sorted array, or an unsorted array if sorting fails
         */
        private static Object[] convert(@Nullable final Collection<?> value) {
            if (value == null || value.isEmpty()) {
                return new Object[0];
            }

            try {
                return value.stream()
                    .filter(Objects::nonNull)
                    .sorted()
                    .toArray();
            } catch (final ClassCastException ex) {
                log.warn("Sorting failed for collection of type: {}. Example contents: {}.\n"
                         + "Consider adding a custom serializer or comparator.", value.getClass().getSimpleName(),
                    value.stream().limit(3).toList());

                return value.toArray();
            }
        }
    }
}
