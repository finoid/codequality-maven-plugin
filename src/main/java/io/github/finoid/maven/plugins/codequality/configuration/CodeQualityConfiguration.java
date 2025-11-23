package io.github.finoid.maven.plugins.codequality.configuration;

import io.github.finoid.maven.plugins.codequality.ConsolePlainViolationReporter;
import io.github.finoid.maven.plugins.codequality.ConsoleTableViolationReporter;
import io.github.finoid.maven.plugins.codequality.GitLabFileViolationReporter;
import io.github.finoid.maven.plugins.codequality.log.LogLevel;
import lombok.Data;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.HashSet;
import java.util.Set;

/**
 * The configuration for the maven code-quality plugin.
 */
@Data
public class CodeQualityConfiguration {
    /**
     * Whether the code-quality analyzer should be enabled or disabled.
     */
    @Parameter(property = "cq.enabled")
    private boolean enabled = true;

    /**
     * The log level of the separate steps - such as error-prone, nullaway and checkstyle.
     */
    @Parameter(property = "cq.stepLogLevel")
    private LogLevel stepLogLevel = LogLevel.ERROR;

    @Parameter
    private CheckstyleConfiguration checkstyle = new CheckstyleConfiguration();

    @Parameter
    private ErrorProneConfiguration errorProne = new ErrorProneConfiguration();

    @Parameter
    private CheckerFrameworkConfiguration checkerFramework = new CheckerFrameworkConfiguration();

    /**
     * List of annotation processor paths.
     */
    @Parameter(defaultValue = "${annotationProcessorPaths}", readonly = true)
    private Set<AnnotationProcessorPaths> annotationProcessorPaths = new HashSet<>();

    /**
     * List of violation reporters by name.
     * <p>
     * Viable options: {@link ConsoleTableViolationReporter#NAME}, {@link ConsolePlainViolationReporter#NAME}, {@link GitLabFileViolationReporter#NAME}
     */
    private Set<String> violationReporters = Set.of(ConsolePlainViolationReporter.NAME, GitLabFileViolationReporter.NAME);

    @Parameter
    private Versions versions = new Versions();

    @Data
    public static class Versions {
        @Parameter(property = "cq.versions.mavenCompiler")
        private String mavenCompiler = "3.13.0";

        @Parameter(property = "cq.versions.mavenClean")
        private String mavenClean = "3.1.0";

        @Parameter(property = "cq.versions.mavenAntRun")
        private String mavenAntRun = "3.1.0";
    }
}
