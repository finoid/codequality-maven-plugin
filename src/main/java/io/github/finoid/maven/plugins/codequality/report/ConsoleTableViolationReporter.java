package io.github.finoid.maven.plugins.codequality.report;

import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestLine;
import de.vandermeer.asciithemes.TA_GridThemes;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import org.apache.maven.plugin.logging.Log;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;

/**
 * A table console-based implementation of {@link ViolationReporter} that logs code quality violations
 * to the Maven console.
 *
 * <p>Violations are categorized into permissive and non-permissive types, and output is formatted
 * accordingly using green (informational) or yellow (warnings) coloring.
 */
@Named("console-table")
@Singleton
public class ConsoleTableViolationReporter extends AbstractConsoleViolationReporter {
    public static final String NAME = "CONSOLE_TABLE";

    /**
     * The width, in characters, the table is rendered at.
     */
    private static final int TABLE_WIDTH = 200;

    /**
     * The padding, in characters, on either side of every cell.
     */
    private static final int CELL_PADDING = 1;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected void logViolations(final Log log, final List<Violation> violations, final PermissiveType permissiveType) {
        logWithLevel(log, permissiveType, System.lineSeparator() + renderTable(violations));
    }

    private static String renderTable(final List<Violation> violations) {
        final AsciiTable table = new AsciiTable();

        // Add the header
        table.addRule();
        table.addRow("Tool", "Rule", "Description", "Path", "Line/Column number");
        table.addRule();

        // Add each individual violation as a row
        violations.forEach(it -> {
            table.addRow(
                it.getTool(),
                it.getRule(),
                it.getDescription(),
                it.getRelativePath(),
                it.getLine() + ":" + it.getColumnNumber());
            table.addRule();
        });

        // Applies to the rows added so far, so it has to happen once every row is in place
        table.setPadding(CELL_PADDING);
        table.setTextAlignment(TextAlignment.LEFT);
        table.getContext()
            .setGridTheme(TA_GridThemes.FULL);

        final CWC_LongestLine cwc = new CWC_LongestLine();
        table.getRenderer()
            .setCWC(cwc);

        // Override the minimum and maximum width of each column
        cwc.add(10, 15)   // Tool
            .add(20, 20)  // Rule
            .add(40, 60)  // Description
            .add(25, 50)  // Path
            .add(12, 20); // Line/Column number

        return table.render(TABLE_WIDTH);
    }
}
