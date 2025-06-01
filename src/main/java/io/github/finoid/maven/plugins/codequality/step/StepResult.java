package io.github.finoid.maven.plugins.codequality.step;

import lombok.Value;
import io.github.finoid.maven.plugins.codequality.report.Severity;
import io.github.finoid.maven.plugins.codequality.report.Violation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Value
public class StepResult {
    StepType type;
    List<Violation> violations;
    boolean isPermissive;

    private StepResult(final StepType type, final List<Violation> violations, final boolean permissive) {
        this.type = type;
        this.violations = new ArrayList<>(violations);
        this.isPermissive = permissive;
    }

    public List<Violation> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    public long countViolationsBySeverityThreshold(final Severity severity) {
        return violations.stream()
            .filter(violation -> violation.getSeverity().isHigherThanOrEqual(severity))
            .count();
    }

    public boolean isNonPermissive() {
        return !isPermissive;
    }

    @SafeVarargs
    public static StepResult create(final StepType type, final boolean permissive, final List<Violation>... violations) {
        final List<Violation> combinedViolations = new ArrayList<>();

        for (final List<Violation> result : violations) {
            combinedViolations.addAll(result);
        }

        return new StepResult(type, combinedViolations, permissive);
    }
}
