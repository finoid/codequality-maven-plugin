package io.github.finoid.maven.plugins.codequality.util;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class Precondition {
    private Precondition() {
    }

    /**
     * Checks if the provided subject is not null. If it is null, a NullPointerException is thrown with the message
     * provided by the messageSupplier.
     *
     * @param <T>     the type of the subject
     * @param subject the subject to check for null
     * @return the subject.
     * @throws NullPointerException if the subject is null
     */
    public static <T> T nonNull(@Nullable final T subject) {
        if (subject == null) {
            throw new NullPointerException("The subject must not be null");
        }

        return subject;
    }

    /**
     * Checks if the provided subject is not null. If it is null, a NullPointerException is thrown with the message
     * provided by the messageSupplier.
     *
     * @param <T>             the type of the subject
     * @param subject         the subject to check for null
     * @param messageSupplier a supplier for the exception message
     * @return the subject
     * @throws NullPointerException if the subject is null
     */
    public static <T> T nonNull(@Nullable final T subject, final Supplier<String> messageSupplier) {
        if (subject == null) {
            throw new NullPointerException(messageSupplier.get());
        }

        return subject;
    }

    /**
     * Checks if the provided subject is not null. If it is null, a NullPointerException is thrown with the message
     * provided by the messageSupplier.
     *
     * @param subject the subject to check for null
     * @param message the exception message.
     * @param <T>     the type of the subject
     * @return the subject
     * @throws NullPointerException if the subject is null
     */
    public static <T> T nonNull(@Nullable final T subject, final String message) {
        if (subject == null) {
            throw new NullPointerException(message);
        }

        return subject;
    }

    /**
     * Checks if the provided subject is not blank. If it is blank, a IllegalArgumentException is thrown with the message
     * provided by the messageSupplier.
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
    private static boolean isBlank(@Nullable final CharSequence cs) {
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

    private static int length(@Nullable final CharSequence cs) {
        return cs == null ? 0 : cs.length();
    }
}
