package io.github.finoid.maven.plugins.codequality.report;

import io.github.finoid.maven.plugins.codequality.ExecutionContext;
import io.github.finoid.maven.plugins.codequality.filter.Violations;
import io.github.finoid.maven.plugins.codequality.fixtures.RecordingLog;
import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class ConsoleTableViolationReporterUnitTest extends UnitTest {
    private static final String COLUMN_DELIMITER = "│";
    private static final String TABLE_TOP_LEFT_CORNER = "┌";

    private final ConsoleTableViolationReporter unit = new ConsoleTableViolationReporter();

    private final RecordingLog log = new RecordingLog();

    @Test
    void givenNoViolations_whenReport_thenReportsBothCategoriesAsEmpty() {
        unit.report(executionContext(), new Violations(List.of(), List.of()));

        snapshotText(log.render());
    }

    @Test
    void givenPermissiveViolations_whenReport_thenRendersTable() {
        unit.report(executionContext(), new Violations(violations(), List.of()));

        snapshotText(log.render());
    }

    @Test
    void givenNonPermissiveViolations_whenReport_thenRendersTable() {
        unit.report(executionContext(), new Violations(List.of(), violations()));

        snapshotText(log.render());
    }

    /**
     * Every row of the table, the last one included, has to be padded. The padding of an
     * {@link de.vandermeer.asciitable.AsciiTable} is applied to the rows added so far, which makes it easy to
     * leave the row added last unpadded.
     */
    @Test
    void givenViolations_whenReport_thenEveryRowIsPadded() {
        unit.report(executionContext(), new Violations(violations(), List.of()));

        final List<String> rows = tableRowsOf(log.render());

        Assertions.assertFalse(rows.isEmpty(), "Expected the rendered table to hold rows");

        rows.forEach(row -> cellsOf(row).forEach(cell -> {
            Assertions.assertTrue(cell.startsWith(" "), () -> "Cell isn't padded to the left: '" + cell + "' of row '" + row + "'");
            Assertions.assertTrue(cell.endsWith(" "), () -> "Cell isn't padded to the right: '" + cell + "' of row '" + row + "'");
        }));
    }

    /**
     * The violations themselves, and not only the header of the category, have to be reported as a warning.
     * Reported as info they are dropped by a quiet build, leaving behind a warning without the violations it refers to.
     */
    @Test
    void givenNonPermissiveViolations_whenReport_thenTableIsReportedAsWarning() {
        unit.report(executionContext(), new Violations(List.of(), violations()));

        assertTableReportedAtLevel("[WARN]");
    }

    @Test
    void givenPermissiveViolations_whenReport_thenTableIsReportedAsInfo() {
        unit.report(executionContext(), new Violations(violations(), List.of()));

        assertTableReportedAtLevel("[INFO]");
    }

    private void assertTableReportedAtLevel(final String level) {
        final List<String> tableEntries = log.entries()
            .stream()
            .filter(it -> it.contains(TABLE_TOP_LEFT_CORNER))
            .toList();

        Assertions.assertEquals(1, tableEntries.size(), "Expected the table to be reported exactly once");

        tableEntries.forEach(it -> Assertions.assertTrue(it.startsWith(level), () -> "Expected the table to be reported at " + level));
    }

    private ExecutionContext executionContext() {
        return ExecutionContext.of(new MavenProject(), log);
    }

    /**
     * Extracts the rows, the horizontal rules excluded, of the rendered table.
     */
    private static List<String> tableRowsOf(final String rendered) {
        return rendered.lines()
            .filter(line -> line.startsWith(COLUMN_DELIMITER))
            .toList();
    }

    /**
     * Extracts the cells, the leading and trailing delimiter excluded, of a rendered row.
     */
    private static List<String> cellsOf(final String row) {
        final String[] parts = row.split(COLUMN_DELIMITER, -1);

        return Arrays.asList(parts)
            .subList(1, parts.length - 1);
    }

    private static List<Violation> violations() {
        return List.of(
            violation("checkstyle", "UnusedImports", "Unused import - java.util.Optional.", "src/main/java/Alpha.java", 3, 8),
            violation("checkstyle", "LineLength", "Line is longer than 160 characters.", "src/main/java/Beta.java", 42, 1),
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
