package io.github.finoid.maven.plugins.codequality.exceptions;

public class CodeQualityException extends RuntimeException {
    public CodeQualityException(final String message) {
        super(message);
    }

    public CodeQualityException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
