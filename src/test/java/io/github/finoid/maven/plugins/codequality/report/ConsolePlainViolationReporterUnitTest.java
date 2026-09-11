package io.github.finoid.maven.plugins.codequality.report;

import io.github.finoid.maven.plugins.codequality.ExecutionContext;
import io.github.finoid.maven.plugins.codequality.filter.Violations;
import io.github.finoid.maven.plugins.codequality.fixtures.RecordingLog;
import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import io.github.finoid.maven.plugins.codequality.log.ViolationLinkableConsoleLogger;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class ConsolePlainViolationReporterUnitTest extends UnitTest {
    private final ConsolePlainViolationReporter unit = new ConsolePlainViolationReporter(new ViolationLinkableConsoleLogger());

    private final RecordingLog log = new RecordingLog();

    @Test
    void givenNoViolations_whenReport_thenReportsBothCategoriesAsEmpty() {
        unit.report(executionContext(), new Violations(List.of(), List.of()));

        snapshotText(log.render());
    }

    @Test
    void givenPermissiveViolations_whenReport_thenReportsEveryViolation() {
        unit.report(executionContext(), new Violations(violations(), List.of()));

        snapshotText(log.render());
    }

    @Test
    void givenNonPermissiveViolations_whenReport_thenReportsEveryViolation() {
        unit.report(executionContext(), new Violations(List.of(), violations()));

        snapshotText(log.render());
    }

    @Test
    void givenViolationsOfBothCategories_whenReport_thenReportsEachCategoryAtItsOwnLevel() {
        unit.report(executionContext(), new Violations(violations(), violations()));

        snapshotText(log.render());
    }

    /**
     * Every non-permissive violation, and not only the header of the category, has to be reported as a warning.
     * Reported as info they are dropped by a quiet build, leaving behind a warning without the violations it refers to.
     */
    @Test
    void givenNonPermissiveViolations_whenReport_thenEveryViolationIsReportedAsWarning() {
        unit.report(executionContext(), new Violations(List.of(), violations()));

        final List<String> entries = log.entries();

        Assertions.assertEquals(violations().size() + 2, entries.size(),
            "Expected a header per category, plus an entry per violation");

        entries.stream()
            .filter(it -> it.contains("file://"))
            .forEach(it -> Assertions.assertTrue(it.startsWith("[WARN]"), () -> "Expected the violation to be reported as a warning: " + it));
    }

    private ExecutionContext executionContext() {
        return ExecutionContext.of(new MavenProject(), log);
    }

    private static List<Violation> violations() {
        return List.of(
            violation("checkstyle", "UnusedImports", "Unused import - java.util.Optional.", "src/main/java/Alpha.java", 3, 8),
            violation("NullAway", "NullAway", "Passing @Nullable parameter 'name' where @NonNull is required.", "src/main/java/Gamma.java", 17, 24));
    }

    private static Violation violation(final String tool, final String rule, final String description, final String path,
                                       final int line, final int columnNumber) {
        return Violation.builder()
            .tool(tool)
            .rule(rule)
            .description(description)
            .fingerprint(tool + ":" + rule + ":" + path)
            .severity(Severity.MAJOR)
            .relativePath(path)
            .fullPath("/workspace/" + path)
            .line(line)
            .columnNumber(columnNumber)
            .build();
    }
}
