package io.github.finoid.maven.plugins.codequality.step;

import io.github.finoid.maven.plugins.codequality.report.Severity;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.Comparator;
import java.util.List;

@Value
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class StepResults {
    List<ProjectStepResults> results;

    public static StepResults ofResults(final List<ProjectStepResults> results) {
        return new StepResults(results);
    }


    /**
     * Retrieve violations across all project steps have severity equal to or higher than the specified threshold.
     *
     * @param severity the minimum severity level to include
     * @return the violations meeting or exceeding the given severity.
     */
    public List<Violation> getViolations(final Severity severity) {
        return results.stream()
            .flatMap(it -> it.getResults().stream())
            .flatMap(it -> it.getViolations().stream())
            .filter(violation -> violation.getSeverity().isHigherThanOrEqual(severity))
            .sorted(Comparator.comparing(Violation::getSeverity))
            .toList();
    }

    /**
     * Retrieve violations across all project steps have severity equal to or higher than the specified threshold and might be permissive or not.
     *
     * @param severity     the minimum severity level to include
     * @param isPermissive whether to include permissive violations or not.
     *                     If true, permissive violations will be included, otherwise non-permissive violations will be included.
     * @return the violations meeting or exceeding the given severity, and might be permissive or not.
     */
    public List<Violation> getViolations(final Severity severity, final boolean isPermissive) {
        return results.stream()
            .flatMap(it -> it.getResults().stream())
            .filter(stepResult -> stepResult.isPermissive() == isPermissive)
            .flatMap(it -> it.getViolations().stream())
            .filter(violation -> violation.getSeverity().isHigherThanOrEqual(severity))
            .sorted(Comparator.comparing(Violation::getSeverity))
            .toList();
    }

    /**
     * Retrieve violations across all project steps that are non-permissive
     * and have severity equal to or higher than the specified threshold.
     *
     * @param severity the minimum severity level to include
     * @return the non-permissive violations meeting or exceeding the given severity
     */
    public List<Violation> getNonPermissiveViolations(final Severity severity) {
        return getViolations(severity, false);
    }
}
