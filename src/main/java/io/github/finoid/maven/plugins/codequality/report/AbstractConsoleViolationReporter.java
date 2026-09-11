package io.github.finoid.maven.plugins.codequality.report;

import io.github.finoid.maven.plugins.codequality.ExecutionContext;
import io.github.finoid.maven.plugins.codequality.filter.Violations;
import org.apache.maven.plugin.logging.Log;

import java.util.List;
import java.util.Locale;

/**
 * A base for console-based {@link ViolationReporter} implementations.
 *
 * <p>Handles what every console reporter does the same way: splitting the violations into permissive and
 * non-permissive groups, emitting the header of each group, and choosing the log level the group is reported at.
 * Subclasses decide how the violations themselves are rendered, through {@link #logViolations}.
 */
abstract class AbstractConsoleViolationReporter implements ViolationReporter {
    protected static final String GREEN = "\u001B[32m";
    protected static final String YELLOW = "\u001B[33m";
    protected static final String RESET = "\u001B[0m";

    /**
     * Reports the collected violations by grouping them into permissive and non-permissive categories,
     * and logs each group with formatting and category-based log levels.
     *
     * @param context    the context of the current mojo execution
     * @param violations the results of executed code analysis steps containing violations
     */
    @Override
    public void report(final ExecutionContext context, final Violations violations) {
        final Log log = context.getLog();

        logViolationsForType(log, violations.getPermissiveViolations(), PermissiveType.PERMISSIVE);
        logViolationsForType(log, violations.getNonPermissiveViolations(), PermissiveType.NON_PERMISSIVE);
    }

    /**
     * Renders and logs a group of violations. The header of the group has already been logged.
     *
     * @param log            the log of the current mojo execution
     * @param violations     the violations of the group, never empty
     * @param permissiveType the category the violations belong to
     */
    protected abstract void logViolations(final Log log, final List<Violation> violations, final PermissiveType permissiveType);

    /**
     * Logs the message as a warning for non-permissive violations, and as info otherwise.
     *
     * @param log            the log of the current mojo execution
     * @param permissiveType the category the message relates to
     * @param message        the message to log
     */
    protected static void logWithLevel(final Log log, final PermissiveType permissiveType, final String message) {
        if (permissiveType == PermissiveType.NON_PERMISSIVE) {
            log.warn(message);
        } else {
            log.info(message);
        }
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

        logViolations(log, violations, permissiveType);
    }

    protected enum PermissiveType {
        PERMISSIVE,
        NON_PERMISSIVE;

        String displayName() {
            return name().replace('_', ' ')
                .toLowerCase(Locale.ROOT);
        }
    }
}
