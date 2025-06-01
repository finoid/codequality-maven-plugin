package io.github.finoid.maven.plugins.codequality.step;

import io.github.finoid.maven.plugins.codequality.fixtures.ProjectStepResultsFaker;
import io.github.finoid.maven.plugins.codequality.fixtures.StepResultFaker;
import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import io.github.finoid.maven.plugins.codequality.fixtures.ViolationFaker;
import io.github.finoid.maven.plugins.codequality.report.Severity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class StepResultsUnitTest extends UnitTest {
    @Test
    void givenNonPermissiveViolation_whenGetNonPermissiveViolations_thenReturnsExpectedViolation() {
        var nonPermissiveProjectStepResults = ProjectStepResultsFaker.projectStepResults()
            .withStepResult(StepResultFaker.stepResultFaker()
                .withIsPermissive(false)
                .withViolation(ViolationFaker.violation().create())
                .create())
            .create();

        var unit = StepResults.ofResults(List.of(nonPermissiveProjectStepResults));

        var result = unit.getNonPermissiveViolations(Severity.INFO);

        snapshot(result);
    }

    @Test
    void givenPermissiveViolation_whenGetNonPermissiveViolations_thenReturnsNoViolation() {
        var permissiveProjectStepResults = ProjectStepResultsFaker.projectStepResults()
            .withStepResult(StepResultFaker.stepResultFaker()
                .withIsPermissive(true)
                .withViolation(ViolationFaker.violation().create())
                .create())
            .create();

        var unit = StepResults.ofResults(List.of(permissiveProjectStepResults));

        var result = unit.getNonPermissiveViolations(Severity.INFO);

        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void givenNonPermissiveViolation_whenGetNonPermissiveViolationsAndHigherSeverity_thenReturnsNoViolation() {
        var nonPermissiveProjectStepResults = ProjectStepResultsFaker.projectStepResults()
            .withStepResult(StepResultFaker.stepResultFaker()
                .withIsPermissive(true)
                .withViolation(ViolationFaker.violation()
                    .withSeverity(Severity.INFO)
                    .create())
                .create())
            .create();

        var unit = StepResults.ofResults(List.of(nonPermissiveProjectStepResults));

        var result = unit.getNonPermissiveViolations(Severity.MINOR);

        Assertions.assertTrue(result.isEmpty());
    }
}