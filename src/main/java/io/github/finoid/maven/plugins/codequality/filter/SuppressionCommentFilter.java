package io.github.finoid.maven.plugins.codequality.filter;

import io.github.finoid.maven.plugins.codequality.report.Violation;
import org.jspecify.annotations.Nullable;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Drops violations a suppression comment in the source has opted out of.
 * <p>
 * A comment naming the rule, on the line the violation is reported against or on the line above it, suppresses that
 * one rule at that one place:
 * <pre>
 * // suppress:NullAway the framework guarantees this is set before the first call
 * private String name;
 *
 * private String name; // suppress:NullAway same, on the line itself
 * </pre>
 * <p>
 * The rule has to be named, and named exactly as the report does. A bare {@code // suppress} is deliberately not
 * honoured: it would silently swallow the next, unrelated finding on the same line, which is the failure mode a
 * suppression mechanism most needs to avoid.
 * <p>
 * The filter works off the reported position rather than off the source language, so it applies to every analyzer -
 * Checkstyle, Error Prone, the Checker Framework and ArchUnit alike. ArchUnit is the reason it cannot be done the
 * other way around: it reads bytecode, which carries no comments, so a rule can never see the suppression itself.
 */
@Named("suppression-comment")
@Singleton
public class SuppressionCommentFilter implements ViolationFilter {
    public static final String NAME = "SUPPRESSION_COMMENT";

    /**
     * Captures every rule named on a line, so several suppressions can share one comment.
     */
    private static final Pattern SUPPRESSION = Pattern.compile("suppress:(\\S+)");

    @Override
    public Violations filter(final Violations violations, final Context context) {
        final SourceLines sourceLines = new SourceLines(context);

        final List<Violation> permissive = new ArrayList<>();
        final List<Violation> nonPermissive = new ArrayList<>();
        final List<Violation> suppressed = new ArrayList<>();

        for (final Violation violation : violations.getPermissiveViolations()) {
            (isSuppressed(violation, sourceLines) ? suppressed : permissive).add(violation);
        }

        for (final Violation violation : violations.getNonPermissiveViolations()) {
            (isSuppressed(violation, sourceLines) ? suppressed : nonPermissive).add(violation);
        }

        report(suppressed, context);

        return new Violations(permissive, nonPermissive);
    }

    @Override
    public String name() {
        return NAME;
    }

    /**
     * A suppression is never silent: how many were honoured is logged unconditionally, so a build which reports
     * nothing can still be told apart from one whose findings were all opted out of.
     */
    private static void report(final List<Violation> suppressed, final Context context) {
        if (suppressed.isEmpty()) {
            return;
        }

        context.getLog()
            .info(String.format("Suppressed %d violation(s) by comment", suppressed.size()));

        suppressed.forEach(violation -> context.getLog()
            .debug(String.format("Suppressed [%s] at %s:%d", violation.getRule(), violation.getRelativePath(), violation.getLine())));
    }

    private static boolean isSuppressed(final Violation violation, final SourceLines sourceLines) {
        final int line = violation.getLine() == null ? 0 : violation.getLine();

        // The line the violation sits on, and the line above it, which is where an own line comment goes
        return namesRule(sourceLines.at(violation.getFullPath(), line), violation.getRule())
            || namesRule(sourceLines.at(violation.getFullPath(), line - 1), violation.getRule());
    }

    private static boolean namesRule(final @Nullable String line, final @Nullable String rule) {
        if (line == null || rule == null) {
            return false;
        }

        final Matcher matcher = SUPPRESSION.matcher(line);

        while (matcher.find()) {
            if (rule.equals(matcher.group(1))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Reads the source files once each, since a file tends to carry more than one violation.
     */
    private static final class SourceLines {
        private final Map<String, List<String>> linesByPath = new HashMap<>();
        private final Context context;

        private SourceLines(final Context context) {
            this.context = context;
        }

        @Nullable
        private String at(final @Nullable String path, final int line) {
            if (path == null || line < 1) {
                return null;
            }

            final List<String> lines = linesOf(path);

            return line > lines.size() ? null : lines.get(line - 1);
        }

        private List<String> linesOf(final String path) {
            return linesByPath.computeIfAbsent(path, this::readLines);
        }

        private List<String> readLines(final String path) {
            final Path file = Path.of(path);

            if (!Files.isRegularFile(file)) {
                return List.of();
            }

            try {
                return Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (final IOException e) {
                // An unreadable source is not a reason to fail the build; the violation simply stays unsuppressed
                context.getLog()
                    .warn(String.format("Could not read [%s] to look for suppression comments. Cause: %s", path, e.getMessage()));

                return List.of();
            }
        }
    }
}
