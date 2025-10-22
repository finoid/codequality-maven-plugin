package io.github.finoid.maven.plugins.codequality;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.finoid.maven.plugins.codequality.exceptions.ReportRendererException;
import io.github.finoid.maven.plugins.codequality.report.Severity;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.report.gitlab.GitLabViolation;
import io.github.finoid.maven.plugins.codequality.step.StepResults;
import io.github.finoid.maven.plugins.codequality.util.ProjectUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.component.annotations.Component;

import javax.inject.Inject;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/**
 * A {@link ViolationReporter} implementation that serializes code quality violations
 * into a JSON file formatted for GitLab's code quality widget integration.
 *
 * <p>Violations of severity {@link Severity#INFO} and above are written to
 * {@code target/gitlab-violations.json} in the current Maven project directory. The
 * report follows GitLab's expected format for code quality reports, allowing it to be
 * used in merge request pipelines for inline feedback.
 *
 * <p>This reporter uses Jackson for JSON serialization and can be customized via a
 * provided {@link ObjectMapper} if needed.
 */
@Component(role = ViolationReporter.class, hint = "gitlab-file")
public class GitLabFileViolationReporter implements ViolationReporter {
    private final ObjectMapper objectMapper;
    private final MavenSession mavenSession;

    @Inject
    public GitLabFileViolationReporter(final MavenSession mavenSession) {
        this(mavenSession, defaultObjectMapper());
    }

    public GitLabFileViolationReporter(final MavenSession mavenSession, final ObjectMapper objectMapper) {
        this.mavenSession = mavenSession;
        this.objectMapper = objectMapper;
    }

    @Override
    public void report(final Log log, final StepResults stepResults) {
        final List<GitLabViolation> gitLabViolations = stepResults.getViolations(Severity.INFO).stream()
            .map(GitLabFileViolationReporter::gitLabViolationOf)
            .toList();

        final String targetOutputFile = ProjectUtils.getProjectBuildDirectory(mavenSession) + "/gitlab-violations.json";

        try (final OutputStream outputStream = new FileOutputStream(targetOutputFile)) {
            objectMapper.writeValue(outputStream, gitLabViolations);
        } catch (final Exception e) {
            throw new ReportRendererException("Error during generation of code quality report", e);
        }
    }

    private static GitLabViolation gitLabViolationOf(final Violation violation) {
        return new GitLabViolation(
            violation.getDescription(),
            violation.getFingerprint(),
            violation.getSeverity().name().toLowerCase(Locale.ENGLISH),
            new GitLabViolation.Location(violation.getRelativePath(), new GitLabViolation.Lines(violation.getLine())));
    }

    private static ObjectMapper defaultObjectMapper() {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.getFactory().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        return objectMapper;
    }
}
