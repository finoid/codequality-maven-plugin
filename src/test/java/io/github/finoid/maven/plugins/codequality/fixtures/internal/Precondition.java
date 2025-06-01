package io.github.finoid.maven.plugins.codequality.fixtures.internal;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class Precondition {
    private Precondition() {
    }

    /**
     * Checks if the provided subject is not null. If it is null, a {@link IllegalArgumentException} is thrown with the message
     * provided by the messageSupplier.
     *
     * @param <T>     the type of the subject
     * @param subject the subject to check for null
     * @return the subject.
     * @throws IllegalArgumentException if the subject is null
     */
    public static <T> T nonNull(@Nullable final T subject) {
        if (subject == null) {
            throw new IllegalArgumentException("The subject must not be null");
        }

        return subject;
    }

    /**
     * Checks if the provided subject is not null. If it is null, a {@link IllegalArgumentException} is thrown with the message
     * provided by the {@code messageSupplier}.
     *
     * @param <T>             the type of the subject
     * @param subject         the subject to check for null
     * @param messageSupplier a supplier for the exception message
     * @return the subject
     * @throws IllegalArgumentException if the subject is null
     */
    public static <T> T nonNull(@Nullable final T subject, final Supplier<String> messageSupplier) {
        if (subject == null) {
            throw new IllegalArgumentException(messageSupplier.get());
        }

        return subject;
    }

    /**
     * Checks if the provided subject is not null. If it is null, a {@link IllegalArgumentException} is thrown with the message
     * provided by the {@code messageSupplier}.
     *
     * @param subject the subject to check for null
     * @param message the exception message.
     * @param <T>     the type of the subject
     * @return the subject
     * @throws IllegalArgumentException if the subject is null
     */
    public static <T> T nonNull(@Nullable final T subject, final String message) {
        if (subject == null) {
            throw new IllegalArgumentException(message);
        }

        return subject;
    }

    /**
     * Checks if the provided subject is not blank. If it is blank, a {@link IllegalArgumentException} is thrown with the message
     * provided by the {@code messageSupplier}.
     *
     * @param subject the subject to check for blank
     * @param message the exception message.
     * @return the subject
     * @throws IllegalArgumentException in case the subject is blank
     */
    @SuppressWarnings("NullAway")
    public static CharSequence nonBlank(@Nullable final CharSequence subject, final String message) {
        if (isBlank(subject)) {
            throw new IllegalArgumentException(message);
        }

        return subject;
    }

    @SuppressWarnings({"NullAway", "argument"})
    public static boolean isBlank(@Nullable final CharSequence cs) {
        final int strLen = length(cs);
        if (strLen == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotBlank(@Nullable final CharSequence cs) {
        return !isBlank(cs);
    }

    private static int length(@Nullable final CharSequence cs) {
        return cs == null ? 0 : cs.length();
    }
}
