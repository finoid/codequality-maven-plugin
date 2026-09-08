package io.github.finoid.maven.plugins.codequality.step;

import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.SeverityLevel;
import io.github.finoid.maven.plugins.codequality.report.Severity;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import lombok.SneakyThrows;
import org.apache.maven.execution.MavenSession;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;

/**
 * Converts the raw output of the analyzers into {@link Violation violations}.
 * <p>
 * Violation paths are relativized against the root of the reactor rather than against the module they were found in.
 * The results of every module end up in a single report, so a module relative path would be ambiguous, and the diff
 * coverage filter matches the paths against a git diff, whose paths are relative to the root of the repository.
 * <p>
 * Only reactor wide state - identical for, and shared by, every builder thread - is read from the session, which makes
 * the converter safe to share between the modules of a parallel build.
 */
@Singleton
public class ViolationConverter {
    private final MavenSession session;

    @Inject
    public ViolationConverter(final MavenSession session) {
        this.session = Precondition.nonNull(session, "MavenSession shouldn't be null");
    }

    public Violation ofAuditEvent(final AuditEvent auditEvent) {
        final File repositoryRoot = repositoryRoot();

        return Violation.builder()
            .tool("Checkstyle")
            .description(auditEvent.getMessage())
            .fingerprint(fingerprint(repositoryRoot, auditEvent))
            .severity(severity(auditEvent.getSeverityLevel()))
            .relativePath(relativePath(repositoryRoot, auditEvent.getFileName()))
            .fullPath(auditEvent.getFileName().replace("\\", "/")) // Windows compatibility
            .line(lineNumber(auditEvent))
            .columnNumber(auditEvent.getColumn())
            .rule(sourceName(auditEvent) + "(" + auditEvent.getViolation().getKey() + ")")
            .build();
    }

    public Violation ofErrorProneViolationMatcher(final Matcher violationMatcher) {
        final File repositoryRoot = repositoryRoot();

        final String columnNumber = violationMatcher.group("column");
        final String absoluteFilePath = violationMatcher.group("path");
        final String description = violationMatcher.group("description");
        final int lineNumber = Integer.parseInt(violationMatcher.group("line"));
        final String rule = violationMatcher.group("rule");

        final int column = columnNumber == null ? 0 : Integer.parseInt(columnNumber);

        return Violation.builder()
            .tool("ErrorProne")
            .description(description)
            .fingerprint(fingerprint(repositoryRoot, absoluteFilePath, description, lineNumber, columnNumber))
            .severity(Severity.MINOR)
            .relativePath(relativePath(repositoryRoot, absoluteFilePath))
            .fullPath(absoluteFilePath.replace("\\", "/")) // Windows compatibility
            .line(lineNumber)
            .columnNumber(column)
            .rule(rule)
            .build();
    }

    public Violation ofCheckerFrameworkViolationMatcher(final Matcher violationMatcher) {
        final File repositoryRoot = repositoryRoot();

        final String columnNumber = violationMatcher.group("column");
        final String absoluteFilePath = violationMatcher.group("path");
        final String description = violationMatcher.group("description");
        final int lineNumber = Integer.parseInt(violationMatcher.group("line"));
        final String rule = violationMatcher.group("rule");

        return Violation.builder()
            .tool("CheckerFramework")
            .description(description)
            .fingerprint(fingerprint(repositoryRoot, absoluteFilePath, description, lineNumber, columnNumber))
            .severity(Severity.MINOR)
            .relativePath(relativePath(repositoryRoot, absoluteFilePath))
            .fullPath(absoluteFilePath.replace("\\", "/")) // Windows compatibility
            .line(lineNumber)
            .columnNumber(Integer.valueOf(columnNumber))
            .rule(rule)
            .build();
    }

    /**
     * The directory the reported paths are relative to, being the directory Maven determined to be the root of the
     * multi module project, and the base directory of the top level project of the reactor when Maven could not.
     */
    private File repositoryRoot() {
        final File multiModuleProjectDirectory = session.getRequest()
            .getMultiModuleProjectDirectory();

        if (multiModuleProjectDirectory != null) {
            return multiModuleProjectDirectory;
        }

        return session.getTopLevelProject()
            .getBasedir();
    }

    @SneakyThrows
    private String fingerprint(final File repositoryRoot, final AuditEvent auditEvent) {
        final String key = String.format("%s:%s:%s:%d",
            relativePath(repositoryRoot, auditEvent.getFileName()),
            auditEvent.getSeverityLevel(),
            auditEvent.getMessage(),
            auditEvent.getLine() + auditEvent.getColumn()
        );

        return fingerprint(key);
    }

    @SneakyThrows
    private String fingerprint(final File repositoryRoot, final String path, final String message, final int lineNumber, final String column) {
        final String key = String.format("%s:%s:%s:%s",
            relativePath(repositoryRoot, path),
            "WARNING",
            message,
            lineNumber + column
        );

        return fingerprint(key);
    }

    @SneakyThrows
    private String fingerprint(final String content) {
        final MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        messageDigest.update(content.getBytes(StandardCharsets.UTF_8));

        final byte[] digest = messageDigest.digest();

        return DatatypeConverter.printHexBinary(digest)
            .toLowerCase(Locale.ROOT);
    }

    private String relativePath(final File repositoryRoot, final String absoluteFilePath) {
        final Path absolutePath = Path.of(absoluteFilePath);

        return repositoryRoot.toPath()
            .relativize(absolutePath)
            .toString()
            .replace("\\", "/"); // Windows compatibility
    }

    private static String sourceName(final AuditEvent auditEvent) {
        String sourceName = auditEvent.getSourceName();

        if (sourceName.endsWith("Check")) {
            sourceName = sourceName.substring(0, sourceName.length() - 5);
        }

        return sourceName.substring(sourceName.lastIndexOf(".") + 1);
    }

    private static int lineNumber(final AuditEvent auditEvent) {
        return auditEvent.getLine();
    }

    private Severity severity(final SeverityLevel severity) {
        return switch (severity) {
            case ERROR -> Severity.MAJOR;
            case WARNING -> Severity.MINOR;
            default -> Severity.INFO;
        };
    }
}
