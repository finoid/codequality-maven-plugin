package io.github.finoid.maven.plugins.codequality;

import io.github.finoid.maven.plugins.codequality.log.LogAndFileAppender;
import io.github.finoid.maven.plugins.codequality.log.LogLevel;
import io.github.finoid.maven.plugins.codequality.step.CheckstyleStep;
import io.github.finoid.maven.plugins.codequality.step.ErrorProneStep;
import io.github.finoid.maven.plugins.codequality.util.ConfigurationUtils;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import io.github.finoid.maven.plugins.codequality.util.ProjectUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.execution.MojoExecutionListener;
import org.apache.maven.project.MavenProject;
import org.checkerframework.checker.formatter.qual.ConversionCategory;
import org.checkerframework.checker.formatter.qual.Format;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.LoggerManager;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A listener that decorates the {@code CompilerMojo} with a custom logger to capture and save its output to a specified file.
 * This is particularly useful for recording logs of compiler-based mojos executed via the Maven Compiler Plugin.
 * <p>
 * Requires the plugin to be registered as an extension.
 * See {@link ErrorProneStep} and {@link CheckstyleStep}
 * <p>
 * This class serves as a workaround to enable log collection for {@code CompilerMojo} executions, where specific logging is required, such as
 * for the ErrorProne and Checker Framework steps.
 */
@Component(role = MojoExecutionListener.class)
public class MojoLogDecoratorExecutionListener implements MojoExecutionListener {
    private static final String COMPILER_MOJO = "CompilerMojo";
    private static final Logger LOGGER = new ConsoleLogger(1, "console");

    private final LoggerManager loggerManager;
    private final MavenProject project;
    private final MavenSession mavenSession;

    @Inject
    public MojoLogDecoratorExecutionListener(final LoggerManager loggerManager, final MavenProject project, final MavenSession mavenSession) {
        this.loggerManager = Precondition.nonNull(loggerManager, "LoggerManager shouldn't be null");
        this.project = Precondition.nonNull(project, "MavenProject shouldn't be null");
        this.mavenSession = mavenSession;
    }

    @Override
    public void beforeMojoExecution(final MojoExecutionEvent event) {
        if (!isMojoOfType(event, COMPILER_MOJO)) {
            return;
        }

        @Nullable
        String nullableOutputFileName = null;

        try {
            nullableOutputFileName = switch (stepAnalyzer(event.getExecution().getConfiguration())) {
                case ERROR_PRONE -> StepAnalyzer.ERROR_PRONE.composeFileName(event.getProject().getModel().getArtifactId());
                case CHECKER_FRAMEWORK -> StepAnalyzer.CHECKER_FRAMEWORK.composeFileName(event.getProject().getModel().getArtifactId());
                case OTHER -> null;
            };

            if (StringUtils.isBlank(nullableOutputFileName)) {
                LOGGER.debug("Unknown execution type. Skipping decoration");

                return;
            }

            final LogLevel stepLogLevel = ProjectUtils.stepLogLevelOrFallback(mavenSession, LogLevel.ERROR);

            final Path outputFilePath = targetOutputFilePath(project.getBuild().getDirectory(), nullableOutputFileName);

            final Logger defaultLoggerForMojo = loggerManager.getLoggerForComponent(event.getExecution().getMojoDescriptor().getImplementation());

            event.getMojo()
                .setLog(new LogAndFileAppender(defaultLoggerForMojo, outputFilePath.toFile(), stepLogLevel));
        } catch (final IllegalStateException e) {
            LOGGER.warn("Unable to determine execution type");
        } catch (final FileNotFoundException e) {
            LOGGER.warn(String.format("Unable to read/write. Path: %s. Cause: %s", nullableOutputFileName, e.getMessage()));
        }
    }

    @Override
    public void afterMojoExecutionSuccess(final MojoExecutionEvent event) {
        // No-op
    }

    @Override
    public void afterExecutionFailure(final MojoExecutionEvent event) {
        // No-op
    }

    private static boolean isMojoOfType(final MojoExecutionEvent event, final String type) {
        return event.getMojo()
            .getClass()
            .getSimpleName()
            .equals(type);
    }

    private static Path targetOutputFilePath(final String targetDirectory, final String targetOutputFilename) {
        return Paths.get(targetDirectory, targetOutputFilename);
    }

    private static StepAnalyzer stepAnalyzer(final Xpp3Dom configuration) {
        // Kind of a hack, only way to determine which step analyzer is currently executing
        final String nullableOutputDirectory = ConfigurationUtils.oneOrThrow(configuration, "outputDirectory");

        if (nullableOutputDirectory == null) {
            return StepAnalyzer.OTHER;
        }

        if (nullableOutputDirectory.contains("checker-framework-classes")) {
            return StepAnalyzer.CHECKER_FRAMEWORK;
        }

        if (nullableOutputDirectory.contains("error-prone-classes")) {
            return StepAnalyzer.ERROR_PRONE;
        }

        return StepAnalyzer.OTHER;
    }

    @Getter
    @RequiredArgsConstructor
    private enum StepAnalyzer {
        ERROR_PRONE("errorprone-%s.txt"),
        CHECKER_FRAMEWORK("checkerframework-%s.txt"),
        OTHER("none-%s.txt");

        @Format(ConversionCategory.GENERAL)
        private final String outputFileNameTemplate;

        public String composeFileName(final String artifactId) {
            return String.format(outputFileNameTemplate, artifactId);
        }
    }
}
