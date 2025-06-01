package io.github.finoid.maven.plugins.codequality.fixtures;

/**
 * A generic interface for generating fake instances of a given type.
 * This is useful for testing, prototyping, and seeding data in applications.
 *
 * @param <T> the type to be faked
 */
public interface Faker<T> {
    /**
     * Generates and returns a fake instance of the specified type.
     * The generated instance may contain randomized or pre-defined values
     * depending on the implementation.
     *
     * @return a faked instance of {@code T}
     */
    T create();
}

