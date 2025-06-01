package io.github.finoid.maven.plugins.codequality.step;

import io.github.finoid.maven.plugins.codequality.fixtures.AuditEventFaker;
import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import io.github.finoid.maven.plugins.codequality.report.CheckerFrameworkViolationLogParser;
import io.github.finoid.maven.plugins.codequality.report.ErrorProneViolationLogParser;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;

class ViolationConverterUnitTest extends UnitTest {
    private static Path WORKING_DIRECTORY = Paths.get("")
        .toAbsolutePath();

    @Mock
    private MavenProject project;
    @InjectMocks
    private ViolationConverter unit;

    @Test
    void givenValidLogEntryAndMatcher_whenOfAuditEvent_thenExpectedViolation() {
        Mockito.when(project.getBasedir())
            .thenReturn(WORKING_DIRECTORY.toFile());

        var event = AuditEventFaker.auditEvent()
            .withFileName(WORKING_DIRECTORY + "/src/main/java/io/github/finoid/maven/plugins/codequality"
                          + "/step/CheckerFrameworkStep.java")
            .create();

        var violation = unit.ofAuditEvent(event);

        snapshot(violation, "*..fullPath");
    }

    @Test
    void givenValidLogEntryAndMatcher_whenOfCheckerFrameworkViolationMatcher_thenExpectedViolation() {
        final Matcher violationMatcher = CheckerFrameworkViolationLogParser.VIOLATION_PATTERN.matcher(
            WORKING_DIRECTORY
            + "/src/main/java/io/github/finoid/maven/plugins/codequality/step/CheckerFrameworkStep.java:[10,29] error: [required.method.not.called] "
            + "@MustCall method close may not have been invoked on SpringApplication.run(Application.class, args) or any of its aliases.");
        Mockito.when(project.getBasedir())
            .thenReturn(WORKING_DIRECTORY.toFile());

        violationMatcher.find();

        var violation = unit.ofCheckerFrameworkViolationMatcher(violationMatcher);

        snapshot(violation, "*..fullPath");
    }

    @Test
    void givenValidLogEntryAndMatcher_whenOfErrorProneViolationMatcher_thenExpectedViolation() {
        final Matcher violationMatcher = ErrorProneViolationLogParser.VIOLATION_PATTERN.matcher(
            WORKING_DIRECTORY
            + "/src/main/java/io/github/finoid/library/otel/OpenTelemetryUtils.java:[24,24] [StringCaseLocaleUsage] Specify a `Locale` when calling "
            + "`String#to{Lower,Upper}Case`. (Note: there are multiple suggested fixes; the third may be most appropriate if you're dealing with "
            + "ASCII Strings.)\n"
            + "    (see https://errorprone.info/bugpattern/StringCaseLocaleUsage)\n"
            + "  Did you mean '.toLowerCase(Locale.ROOT);' or '.toLowerCase(Locale.getDefault());' or "
            + "'return Ascii.toLowerCase(currentSpan.getSpanContext()'?");
        Mockito.when(project.getBasedir())
            .thenReturn(WORKING_DIRECTORY.toFile());

        violationMatcher.find();

        var violation = unit.ofErrorProneViolationMatcher(violationMatcher);

        snapshot(violation, "*..fullPath");
    }
}