package io.github.finoid.maven.plugins.codequality.report;

import io.github.finoid.maven.plugins.codequality.ExecutionContext;
import io.github.finoid.maven.plugins.codequality.filter.Violations;

/**
 * A contract for reporting code quality violations to a specified output, such as the console or a file.
 *
 * <p>Implementations are responsible for formatting and emitting violation data based on severity and configuration,
 * typically after all analysis steps have been completed.
 */
public interface ViolationReporter {
    /**
     * Reports the results of code quality analysis.
     *
     * @param context    the context of the mojo execution which aggregates the results of the reactor
     * @param violations the collected violations of all executed code quality steps
     */
    void report(final ExecutionContext context, final Violations violations);

    /**
     * The name of the violation reporter.
     */
    String name();
}
