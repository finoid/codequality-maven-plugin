package io.github.finoid.maven.plugins.codequality.step;

import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.SeverityLevel;
import io.github.finoid.maven.plugins.codequality.report.Severity;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import lombok.SneakyThrows;
import org.apache.maven.project.MavenProject;

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

        return Violation.builder()
            .tool("Checkstyle")
            .description(String.format("%s: %s", "Checkstyle", auditEvent.getMessage()))
            .fingerprint(fingerprint(repositoryRoot, auditEvent))
            .severity(severity(auditEvent.getSeverityLevel()))
            .relativePath(relativePath(repositoryRoot, auditEvent.getFileName()))
            .fullPath(auditEvent.getFileName().replace("\\", "/")) // Windows compatibility
            .line(lineNumber(auditEvent))
            .columnNumber(auditEvent.getColumn())
            .rule(auditEvent.getViolation().getKey() + " - " + sourceName(auditEvent))
            .build();
    }

    public Violation ofErrorProneViolationMatcher(final Matcher violationMatcher) {
        final File repositoryRoot = project.getBasedir();

        final String columnNumber = violationMatcher.group("column");
        final String absoluteFilePath = violationMatcher.group("path");
        final String description = violationMatcher.group("description");
        final int lineNumber = Integer.parseInt(violationMatcher.group("line"));
        final String rule = violationMatcher.group("rule");

        final int column = columnNumber == null ? 0 : Integer.parseInt(columnNumber);

        return Violation.builder()
            .tool("ErrorProne")
            .description(String.format("%s: %s", "ErrorProne", description))
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
        final File repositoryRoot = project.getBasedir();

        final String columnNumber = violationMatcher.group("column");
        final String absoluteFilePath = violationMatcher.group("path");
        final String description = violationMatcher.group("description");
        final int lineNumber = Integer.parseInt(violationMatcher.group("line"));
        final String rule = violationMatcher.group("rule");

        return Violation.builder()
            .tool("CheckerFramework")
            .description(String.format("%s: %s", "CheckerFramework", description))
            .fingerprint(fingerprint(repositoryRoot, absoluteFilePath, description, lineNumber, columnNumber))
            .severity(Severity.MINOR)
            .relativePath(relativePath(repositoryRoot, absoluteFilePath))
            .fullPath(absoluteFilePath.replace("\\", "/")) // Windows compatibility
            .line(lineNumber)
            .columnNumber(Integer.valueOf(columnNumber))
            .rule(rule)
            .build();
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
