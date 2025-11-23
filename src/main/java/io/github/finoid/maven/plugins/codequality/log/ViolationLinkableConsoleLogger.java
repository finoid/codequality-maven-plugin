package io.github.finoid.maven.plugins.codequality.log;

import io.github.finoid.maven.plugins.codequality.report.Violation;

import javax.inject.Singleton;

/**
 * Responsible for formatting violations into linkable, readable console output.
 */
@Singleton
public class ViolationLinkableConsoleLogger {
    /**
     * Formats a {@link Violation} into a structured, console-friendly string.
     * Format: fullPath:[line,column] message [rule]
     *
     * @param violation the violation to format
     * @return formatted string representation of the violation
     */
    @SuppressWarnings("UnnecessaryStringBuilder")
    public String format(final Violation violation) {
        return new StringBuilder() // TODO (nw) keep using string builder and set the actual length?
            .append(violation.getFullPath())
            .append(":[")
            .append(violation.getLine())
            .append(",")
            .append(violation.getColumnNumber())
            .append("] ")
            .append(System.lineSeparator())
            .append(" ")
            .append(" [")
            .append(violation.getTool())
            .append(" - ")
            .append(violation.getRule())
            .append("] ")
            .append(violation.getDescription())
            .toString();
    }
}
