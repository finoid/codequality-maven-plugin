package io.github.finoid.maven.plugins.codequality;

import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestLine;
import de.vandermeer.asciithemes.TA_GridThemes;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import io.github.finoid.maven.plugins.codequality.filter.Violations;
import io.github.finoid.maven.plugins.codequality.report.Severity;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.component.annotations.Component;

import java.util.List;
import java.util.Locale;

/**
 * A table console-based implementation of {@link ViolationReporter} that logs code quality violations
 * to the Maven console.
 *
 * <p>Violations are categorized into permissive and non-permissive types, and output is formatted
 * accordingly using green (informational) or yellow (warnings) coloring.
 */
@Component(role = ViolationReporter.class, hint = "console-table")
public class ConsoleTableViolationReporter implements ViolationReporter {
    public static final String NAME = "CONSOLE_TABLE";

    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RESET = "\u001B[0m";

    /**
     * Reports all violations of at least {@link Severity#MINOR} level by grouping them
     * into permissive and non-permissive categories, and logs each group with formatting
     * and severity-based log levels.
     *
     * @param log        the Maven plugin log interface
     * @param violations the results of executed code analysis steps containing violations
     */
    @Override
    public void report(final Log log, final Violations violations) {
        logViolationsForType(log, violations.getPermissiveViolations(), PermissiveType.PERMISSIVE);
        logViolationsForType(log, violations.getNonPermissiveViolations(), PermissiveType.NON_PERMISSIVE);
    }

    @Override
    public String name() {
        return NAME;
    }

    private String renderTable(final List<Violation> violations) {
        final AsciiTable table = new AsciiTable();

        // Add the header
        table.addRule();
        table.addRow("Tool", "Rule", "Description", "Path", "Line/Column number");
        table.addRule();

        // Add each individual violation as a row
        violations.forEach(it -> {
            table.setPadding(1);

            table.addRow(
                it.getTool(),
                it.getRule(),
                it.getDescription(),
                it.getRelativePath(),
                it.getLine() + ":" + it.getColumnNumber());
            table.addRule();
        });

        table.setTextAlignment(TextAlignment.LEFT);
        table.getContext().setGridTheme(TA_GridThemes.FULL);

        final CWC_LongestLine cwc = new CWC_LongestLine();
        table.getRenderer()
            .setCWC(cwc);

        // Override specific column width ratios (relative percentages)
        cwc.add(10, 15)   // Type
            .add(20, 20)  // Rule
            .add(40, 60)  // Description!)
            .add(25, 50)  // Path
            .add(12, 20); // Column number

        return table.render(200);
    }

    private void logViolationsForType(final Log log, final List<Violation> violations, final PermissiveType permissiveType) {
        if (violations.isEmpty()) {
            log.info(String.format("✅ %s ##### No %s violations found ##### %s ✅ ", GREEN, permissiveType.displayName(), RESET));
            return;
        }

        final String message = String.format("%s ##### found %d %s violations ##### %s",
            (permissiveType == PermissiveType.NON_PERMISSIVE) ? YELLOW : GREEN,
            violations.size(),
            permissiveType.displayName(),
            RESET
        );

        logWithLevel(log, permissiveType, (permissiveType == PermissiveType.NON_PERMISSIVE ? "⚠ " : "✅ ") + message);

        log.info(System.lineSeparator() + renderTable(violations));
    }

    private static void logWithLevel(final Log log, final PermissiveType permissiveType, final String message) {
        if (permissiveType == PermissiveType.NON_PERMISSIVE) {
            log.warn(message);
        } else {
            log.info(message);
        }
    }

    private enum PermissiveType {
        PERMISSIVE,
        NON_PERMISSIVE;

        public String displayName() {
            return name().replace('_', ' ')
                .toLowerCase(Locale.ROOT);
        }
    }
}
