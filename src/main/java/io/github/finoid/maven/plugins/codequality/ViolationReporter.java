package io.github.finoid.maven.plugins.codequality;

import io.github.finoid.maven.plugins.codequality.step.StepResults;
import org.apache.maven.plugin.logging.Log;

/**
 * A contract for reporting code quality violations to a specified output, such as the console or a file.
 *
 * <p>Implementations are responsible for formatting and emitting violation data based on severity and configuration,
 * typically after all analysis steps have been completed.
 */
public interface ViolationReporter {
    /**
     * Reports the results of code quality analysis to the specified Maven log.
     *
     * @param log         the Maven plugin logger used to emit messages
     * @param stepResults the collected results of all executed code quality steps
     */
    void report(final Log log, final StepResults stepResults);
}
