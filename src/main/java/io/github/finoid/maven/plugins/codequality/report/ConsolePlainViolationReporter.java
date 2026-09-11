package io.github.finoid.maven.plugins.codequality.report;

import io.github.finoid.maven.plugins.codequality.log.ViolationLinkableConsoleLogger;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import org.apache.maven.plugin.logging.Log;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;

/**
 * A plain console-based implementation of {@link ViolationReporter} that logs code quality violations
 * to the Maven console using color-coded and linkable formatting.
 *
 * <p>Violations are categorized into permissive and non-permissive types, and output is formatted
 * accordingly using green (informational) or yellow (warnings) coloring.
 */
@Named("console-plain")
@Singleton
public class ConsolePlainViolationReporter extends AbstractConsoleViolationReporter {
    public static final String NAME = "CONSOLE_PLAIN";

    private final ViolationLinkableConsoleLogger violationLinkableConsoleLogger;

    @Inject
    public ConsolePlainViolationReporter(final ViolationLinkableConsoleLogger violationLinkableConsoleLogger) {
        this.violationLinkableConsoleLogger = Precondition.nonNull(violationLinkableConsoleLogger, "ViolationLinkableConsoleLogger shouldn't be null");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected void logViolations(final Log log, final List<Violation> violations, final PermissiveType permissiveType) {
        violations.forEach(it -> logWithLevel(log, permissiveType, violationLinkableConsoleLogger.format(it)));
    }
}
