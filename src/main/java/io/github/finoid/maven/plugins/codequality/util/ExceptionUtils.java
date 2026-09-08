package io.github.finoid.maven.plugins.codequality.util;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * Utility class for extracting the interesting part of an exception chain.
 */
@UtilityClass
public class ExceptionUtils {
    /**
     * A cause chain is not guaranteed to be acyclic, so the traversal is bounded.
     */
    private static final int MAX_DEPTH = 20;

    /**
     * Resolves the message of the deepest cause which has one.
     * <p>
     * The steps fork other mojos, so the reason a step failed regularly sits several wrappers down - a
     * {@code MojoExecutionException} wrapping a {@code CompilationFailureException} wrapping the actual failure - while
     * the wrappers themselves say little. Falls back to the simple name of the deepest cause when none of them carries a
     * message.
     *
     * @param throwable the throwable to unwrap
     * @return the message of the deepest cause which has one, never blank
     */
    public static String rootCauseMessage(final Throwable throwable) {
        Throwable deepest = throwable;
        String message = messageOrNull(throwable);

        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            final Throwable cause = deepest.getCause();

            if (cause == null || cause == deepest) {
                break;
            }

            deepest = cause;

            final String causeMessage = messageOrNull(cause);
            if (causeMessage != null) {
                message = causeMessage;
            }
        }

        return message != null ? message : deepest.getClass().getSimpleName();
    }

    @Nullable
    private static String messageOrNull(final Throwable throwable) {
        final String message = throwable.getMessage();

        if (message == null || message.isBlank()) {
            return null;
        }

        return message.strip();
    }
}
