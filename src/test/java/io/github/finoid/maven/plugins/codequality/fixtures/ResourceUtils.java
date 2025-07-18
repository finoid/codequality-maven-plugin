package io.github.finoid.maven.plugins.codequality.fixtures;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

@UtilityClass
public final class ResourceUtils {

    /**
     * Tries to retrieve an input stream from the given resource name.
     *
     * @param resourceName the name of the resource to retrieve
     * @return the input stream of the resource
     * @throws RuntimeException if the resource could not be found or accessed
     * @throws NullPointerException if the resourceName is null
     */
    public static InputStream tryInputStreamFrom(final String resourceName) {
        Objects.requireNonNull(resourceName);

        @Nullable final InputStream resourceAsStream;
        try {
            resourceAsStream = classLoader().getResourceAsStream(resourceName);

            if (resourceAsStream == null) {
                throw new RuntimeException(
                    String.format("The resourceAsStream could not found and/or accessed. %s", resourceName));
            }
        } catch (final SecurityException | IllegalStateException | Error e) {
            throw new RuntimeException(e);
        }

        return resourceAsStream;
    }

    /**
     * Retrieves an optional input stream from the given resource name.
     *
     * @param resourceName the name of the resource to retrieve
     * @return an optional input stream of the resource, or empty if the resource could not be found or accessed
     * @throws NullPointerException if the resourceName is null
     */
    public static Optional<InputStream> optionalUriFrom(final String resourceName) {
        Objects.requireNonNull(resourceName);

        try {
            @Nullable final InputStream resourceAsStream = classLoader().getResourceAsStream(resourceName);

            return Optional.ofNullable(resourceAsStream);
        } catch (SecurityException | IllegalStateException | Error e) {
            return Optional.empty();
        }
    }

    private static ClassLoader classLoader() {
        return ClassLoader.getSystemClassLoader();
    }

}
