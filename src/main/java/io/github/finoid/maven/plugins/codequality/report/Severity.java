package io.github.finoid.maven.plugins.codequality.report;

public enum Severity {
    INFO,
    MINOR,
    MAJOR,
    CRITICAL,
    BLOCKER;

    @SuppressWarnings("EnumOrdinal")
    public boolean isHigherThanOrEqual(final Severity severity) {
        return this.ordinal() >= severity.ordinal();
    }
}
