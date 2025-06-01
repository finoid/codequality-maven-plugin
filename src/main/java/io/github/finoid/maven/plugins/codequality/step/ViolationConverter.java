package io.github.finoid.maven.plugins.codequality.step;

import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.SeverityLevel;
import lombok.SneakyThrows;
import org.apache.maven.project.MavenProject;
import io.github.finoid.maven.plugins.codequality.report.Severity;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.util.Precondition;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;

@Singleton
public class ViolationConverter {
    private final MavenProject project;

    @Inject
    public ViolationConverter(final MavenProject project) {
        this.project = Precondition.nonNull(project, "MavenProject shouldn't be null");
    }

    public Violation ofAuditEvent(final AuditEvent auditEvent) {
        final File repositoryRoot = project.getBasedir();

        return new Violation(
            String.format("%s: %s", "Checkstyle", auditEvent.getMessage()),
            fingerprint(repositoryRoot, auditEvent),
            severity(auditEvent.getSeverityLevel()),
            relativePath(repositoryRoot, auditEvent.getFileName()),
            auditEvent.getFileName().replace("\\", "/"),  // Windows compatibility
            lineNumber(auditEvent),
            auditEvent.getColumn(),
            auditEvent.getViolation().getKey()
        );
    }

    public Violation ofErrorProneViolationMatcher(final Matcher violationMatcher) {
        final File repositoryRoot = project.getBasedir();

        final String columnNumber = violationMatcher.group("column");
        final String absoluteFilePath = violationMatcher.group("path");
        final String description = violationMatcher.group("description");
        final int lineNumber = Integer.parseInt(violationMatcher.group("line"));
        final String rule = violationMatcher.group("rule");

        return new Violation(
            String.format("%s: %s", "ErrorProne", description),
            fingerprint(repositoryRoot, absoluteFilePath, description, lineNumber, columnNumber),
            Severity.MINOR,
            relativePath(repositoryRoot, absoluteFilePath),
            absoluteFilePath.replace("\\", "/"),  // Windows compatibility
            lineNumber,
            Integer.valueOf(columnNumber),
            rule);
    }

    public Violation ofCheckerFrameworkViolationMatcher(final Matcher violationMatcher) {
        final File repositoryRoot = project.getBasedir();

        final String columnNumber = violationMatcher.group("column");
        final String absoluteFilePath = violationMatcher.group("path");
        final String description = violationMatcher.group("description");
        final int lineNumber = Integer.parseInt(violationMatcher.group("line"));
        final String rule = violationMatcher.group("rule");

        return new Violation(
            String.format("%s: %s", "CheckerFramework", description),
            fingerprint(repositoryRoot, absoluteFilePath, description, lineNumber, columnNumber),
            Severity.MINOR,
            relativePath(repositoryRoot, absoluteFilePath),
            absoluteFilePath.replace("\\", "/"), // Windows compatibility
            lineNumber,
            Integer.valueOf(columnNumber),
            rule);
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

    private static int lineNumber(final AuditEvent auditEvent) {
        return auditEvent.getLine();
    }

    private Severity severity(final SeverityLevel severity) {
        switch (severity) {
            case ERROR:
                return Severity.MAJOR;
            case WARNING:
                return Severity.MINOR;
            default:
                return Severity.INFO;
        }
    }
}
