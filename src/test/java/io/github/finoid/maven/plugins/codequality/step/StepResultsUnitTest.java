package io.github.finoid.maven.plugins.codequality.step;

import io.github.finoid.maven.plugins.codequality.fixtures.ProjectStepResultsFaker;
import io.github.finoid.maven.plugins.codequality.fixtures.StepResultFaker;
import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import io.github.finoid.maven.plugins.codequality.fixtures.ViolationFaker;
import io.github.finoid.maven.plugins.codequality.report.Severity;
import io.github.finoid.maven.plugins.codequality.report.Violation;
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
    void givenBothPermissiveAndNonPermissiveResults_whenGetViolations_thenOnlyTheRequestedBucketIsReturned() {
        var results = StepResults.ofResults(List.of(
            ProjectStepResultsFaker.projectStepResults()
                .withStepResult(StepResultFaker.stepResultFaker()
                    .withIsPermissive(true)
                    .withViolation(ViolationFaker.violation().withSeverity(Severity.MAJOR).create())
                    .create())
                .create(),
            ProjectStepResultsFaker.projectStepResults()
                .withStepResult(StepResultFaker.stepResultFaker()
                    .withIsPermissive(false)
                    .withViolation(ViolationFaker.violation().withSeverity(Severity.CRITICAL).create())
                    .create())
                .create()));

        Assertions.assertEquals(List.of(Severity.MAJOR), severitiesOf(results.getViolations(Severity.INFO, true)));
        Assertions.assertEquals(List.of(Severity.CRITICAL), severitiesOf(results.getViolations(Severity.INFO, false)));
    }

    @Test
    void givenViolationsOfMixedSeverity_whenGetViolations_thenReturnedSortedBySeverity() {
        var results = StepResults.ofResults(List.of(ProjectStepResultsFaker.projectStepResults()
            .withStepResult(StepResult.create(StepType.CHECKSTYLE, false, List.of(
                ViolationFaker.violation().withSeverity(Severity.BLOCKER).create(),
                ViolationFaker.violation().withSeverity(Severity.MINOR).create(),
                ViolationFaker.violation().withSeverity(Severity.MAJOR).create())))
            .create()));

        Assertions.assertEquals(List.of(Severity.MINOR, Severity.MAJOR, Severity.BLOCKER),
            severitiesOf(results.getNonPermissiveViolations(Severity.INFO)));
    }

    @Test
    void givenViolationsBelowTheThreshold_whenGetViolations_thenTheyAreExcluded() {
        var results = StepResults.ofResults(List.of(ProjectStepResultsFaker.projectStepResults()
            .withStepResult(StepResult.create(StepType.CHECKSTYLE, false, List.of(
                ViolationFaker.violation().withSeverity(Severity.INFO).create(),
                ViolationFaker.violation().withSeverity(Severity.MAJOR).create())))
            .create()));

        Assertions.assertEquals(List.of(Severity.MAJOR), severitiesOf(results.getNonPermissiveViolations(Severity.MAJOR)));
    }

    @Test
    void givenNonPermissiveViolation_whenGetNonPermissiveViolationsAndHigherSeverity_thenReturnsNoViolation() {
        var nonPermissiveProjectStepResults = ProjectStepResultsFaker.projectStepResults()
            .withStepResult(StepResultFaker.stepResultFaker()
                .withIsPermissive(false)
                .withViolation(ViolationFaker.violation()
                    .withSeverity(Severity.INFO)
                    .create())
                .create())
            .create();

        var unit = StepResults.ofResults(List.of(nonPermissiveProjectStepResults));

        var result = unit.getNonPermissiveViolations(Severity.MINOR);

        Assertions.assertTrue(result.isEmpty());
    }

    private static List<Severity> severitiesOf(final List<Violation> violations) {
        return violations.stream()
            .map(Violation::getSeverity)
            .toList();
    }
}
