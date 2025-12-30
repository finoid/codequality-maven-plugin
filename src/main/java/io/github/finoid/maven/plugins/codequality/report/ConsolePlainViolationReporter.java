package io.github.finoid.maven.plugins.codequality.report;

import io.github.finoid.maven.plugins.codequality.filter.Violations;
import io.github.finoid.maven.plugins.codequality.log.ViolationLinkableConsoleLogger;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.component.annotations.Component;

import javax.inject.Inject;
import java.util.List;
import java.util.Locale;

/**
 * A plain console-based implementation of {@link ViolationReporter} that logs code quality violations
 * to the Maven console using color-coded and linkable formatting.
 *
 * <p>Violations are categorized into permissive and non-permissive types, and output is formatted
 * accordingly using green (informational) or yellow (warnings) coloring.
 */
@Component(role = ViolationReporter.class, hint = "console-plain")
public class ConsolePlainViolationReporter implements ViolationReporter {
    public static final String NAME = "CONSOLE_PLAIN";

    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RESET = "\u001B[0m";

    private final ViolationLinkableConsoleLogger violationLinkableConsoleLogger;

    @Inject
    public ConsolePlainViolationReporter(final ViolationLinkableConsoleLogger violationLinkableConsoleLogger) {
        this.violationLinkableConsoleLogger = Precondition.nonNull(violationLinkableConsoleLogger, "ViolationLinkableConsoleLogger shouldn't be null");
    }

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

        violations.forEach(v -> logWithLevel(log, permissiveType, violationLinkableConsoleLogger.format(v)));
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
