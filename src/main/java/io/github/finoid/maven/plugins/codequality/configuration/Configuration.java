package io.github.finoid.maven.plugins.codequality.configuration;

/**
 * A marker interface for classes that provides configuration.
 */
public interface Configuration {
    /**
     * Whether the execution should be permissive (allow violations without failing) or strict (fail on violations).
     */
    boolean isPermissive();
}
