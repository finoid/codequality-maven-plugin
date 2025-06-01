package io.github.finoid.maven.plugins.codequality.step;

import lombok.Value;
import io.github.finoid.maven.plugins.codequality.report.Severity;
import io.github.finoid.maven.plugins.codequality.report.Violation;

import java.util.Arrays;
import java.util.List;

/**
 * Represents the aggregated results of multiple code quality steps for a project.
 */
@Value
public class ProjectStepResults {
    String projectName;
    List<StepResult> results;

    /**
     * Retrieves all violations from the contained step results.
     *
     * @return A list of all {@link Violation} objects found in the step results.
     */
    public List<Violation> getViolations() {
        return results.stream()
            .flatMap(it -> it.getViolations().stream())
            .toList();
    }

    /**
     * Counts the number of violations that meet or exceed a given severity threshold.
     *
     * @param severity The minimum severity level to consider.
     * @return The number of violations that have a severity equal to or greater than the specified threshold.
     */
    public long countViolationsBySeverityThreshold(final Severity severity) {
        return getViolations().stream()
            .filter(violation -> violation.getSeverity().isHigherThanOrEqual(severity))
            .count();
    }

    /**
     * Creates a {@link ProjectStepResults} instance from multiple {@link StepResult} objects.
     *
     * @param results The step results to aggregate.
     * @return A new {@link ProjectStepResults} instance containing the provided results.
     */
    public static ProjectStepResults ofResults(final String projectName, final StepResult... results) {
        return new ProjectStepResults(projectName, Arrays.stream(results).toList());
    }
}
