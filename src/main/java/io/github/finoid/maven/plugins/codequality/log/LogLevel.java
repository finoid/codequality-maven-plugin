package io.github.finoid.maven.plugins.codequality.log;

import java.util.Arrays;

public enum LogLevel {
    ERROR, WARN, INFO, DEBUG, TRACE;

    public static LogLevel ofStringOrThrow(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("Invalid log level: null. Valid values are: " + Arrays.toString(values()) + ".");
        }

        return Arrays.stream(values())
            .filter(it -> it.name().equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Invalid log level: " + value + ". Valid values are: " + Arrays.toString(values()) + "."));
    }
}
