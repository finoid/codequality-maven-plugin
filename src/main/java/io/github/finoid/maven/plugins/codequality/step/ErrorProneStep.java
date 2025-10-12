package io.github.finoid.maven.plugins.codequality.step;

import io.github.finoid.maven.plugins.codequality.MavenAnnotationProcessorsManager;
import io.github.finoid.maven.plugins.codequality.configuration.CodeQualityConfiguration;
import io.github.finoid.maven.plugins.codequality.configuration.ErrorProneConfiguration;
import io.github.finoid.maven.plugins.codequality.exceptions.CodeQualityException;
import io.github.finoid.maven.plugins.codequality.report.ErrorProneViolationLogParser;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.util.CollectorUtils;
import io.github.finoid.maven.plugins.codequality.util.MojoUtils.ElementUtils;
import io.github.finoid.maven.plugins.codequality.util.MojoUtils.PluginUtils;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import io.github.finoid.maven.plugins.codequality.util.PropertyUtils;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.twdata.maven.mojoexecutor.MojoExecutor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;

/**
 * Step which executes the ErrorProne analyzer.
 */
@Singleton
public class ErrorProneStep implements Step<ErrorProneConfiguration> {
    @SuppressWarnings("InlineFormatString")
    private static final String ERROR_PRONE_FLAGS_TEMPLATE = "-Xplugin:ErrorProne "
        + "-XepAllErrorsAsWarnings "
        + "-XepDisableWarningsInGeneratedCode "
        + "-Xep:MissingSummary:OFF " // TODO (nw) should be configurable
        + "-Xep:EqualsGetClass:OFF "
        + "-Xep:JavaTimeDefaultTimeZone:OFF "
        + "-XepOpt:NullAway:AnnotatedPackages=%s "
        + "-XepExcludedPaths:%s"; // The maven-compiler-plugin does not like text block

    private final MavenProject project;
    private final MavenSession mavenSession;
    private final BuildPluginManager pluginManager;
    private final ErrorProneViolationLogParser errorProneErrorLogParser;

    @Inject
    public ErrorProneStep(
        final MavenProject project,
        final MavenSession mavenSession,
        final BuildPluginManager pluginManager,
        final ErrorProneViolationLogParser errorProneErrorLogParser
    ) {
        this.project = Precondition.nonNull(project, "MavenProject shouldn't be null");
        this.mavenSession = Precondition.nonNull(mavenSession, "MavenSession shouldn't be null");
        this.pluginManager = Precondition.nonNull(pluginManager, "BuildPluginManager shouldn't be null");
        this.errorProneErrorLogParser = Precondition.nonNull(errorProneErrorLogParser, "ErrorProneErrorLogParser shouldn't be null");
    }

    @Override
    public boolean isEnabled(final ErrorProneConfiguration configuration) {
        return configuration.isEnabled();
    }

    @Override
    public StepType type() {
        return StepType.ERROR_PRONE;
    }

    @Override
    public StepResult execute(final CodeQualityConfiguration codeQualityConfiguration, final ErrorProneConfiguration stepConfiguration, final Log log) {
        final List<Violation> violations = executeStep(codeQualityConfiguration, stepConfiguration, log);

        return StepResult.create(StepType.ERROR_PRONE, stepConfiguration.isPermissive(), violations);
    }

    @Override
    public CleanContext getCleanContext() {
        return CleanContext.DO_NOTHING;
    }

    private List<Violation> executeStep(final CodeQualityConfiguration codeQualityConfiguration, final ErrorProneConfiguration stepConfiguration,
                                        final Log log) {
        final PluginDescriptor descriptor =
            PluginUtils.pluginDescriptor("org.apache.maven.plugins", "maven-compiler-plugin", codeQualityConfiguration.getVersions().getMavenCompiler());

        final String javaVersion = PropertyUtils.valueOrFallback(project.getProperties(), "java.version", "21");

        final MavenProject currentProject = mavenSession.getCurrentProject();

        final File currentProjectArtifactFile = currentProject.getArtifact()
            .getFile();

        try {
            executeMojo(
                PluginUtils.pluginOfDescriptor(descriptor),
                goal("compile"),
                configuration(
                    element(MojoExecutor.name("fork"), "true"), // To be able to apply javac flags, see elementsOfCompilerArgs
                    element(MojoExecutor.name("source"), javaVersion),
                    element(MojoExecutor.name("target"), javaVersion),
                    element(MojoExecutor.name("release"), javaVersion),
                    element("outputDirectory", currentProject.getBuild().getDirectory() + "/error-prone-classes"),
                    element(MojoExecutor.name("showWarnings"), "true"),
                    element(MojoExecutor.name("compilerArgs"), elementsOfCompilerArgs(stepConfiguration)
                        .toArray(MojoExecutor.Element[]::new)),
                    element(MojoExecutor.name("annotationProcessorPaths"),
                        elementsOfAnnotationProcessorPaths(currentProject, codeQualityConfiguration, stepConfiguration)
                            .toArray(MojoExecutor.Element[]::new))
                ),
                executionEnvironment(currentProject, mavenSession, pluginManager)
            );

            // Restores the current project's original artifact file, resolving
            // the error: 'The packaging for this project did not assign a file to the build artifact.'
            currentProject.getArtifact()
                .setFile(currentProjectArtifactFile);

            return parseViolations(log);
        } catch (final Exception e) {
            throw new CodeQualityException("Error during execution of ErrorProne step", e);
        }
    }

    private List<MojoExecutor.Element> elementsOfCompilerArgs(final ErrorProneConfiguration errorProneConfiguration) {
        return CompilerArgsComposer.compose(errorProneConfiguration, mavenSession);
    }

    private List<MojoExecutor.Element> elementsOfAnnotationProcessorPaths(
        final MavenProject currentProject,
        final CodeQualityConfiguration codeQualityConfiguration,
        final ErrorProneConfiguration errorProneConfiguration
    ) {
        final MavenAnnotationProcessorsManager annotationProcessorsManager = new MavenAnnotationProcessorsManager(currentProject, codeQualityConfiguration);

        final List<MojoExecutor.Element> annotationProcessorPaths = annotationProcessorsManager.annotationPaths().stream()
            .map(it -> ElementUtils.annotationProcessor(it.getGroupId(), it.getArtifactId(), it.getVersion()))
            .collect(CollectorUtils.toMutableList());

        final ErrorProneConfiguration.Versions versions = errorProneConfiguration.getVersions();

        annotationProcessorPaths.add(ElementUtils.annotationProcessor("com.google.errorprone", "error_prone_core", versions.getErrorProne()));

        if (errorProneConfiguration.isNullAwayEnabled()) {
            annotationProcessorPaths.add(ElementUtils.annotationProcessor("com.uber.nullaway", "nullaway", versions.getNullAway()));
        }

        return annotationProcessorPaths;
    }

    private List<Violation> parseViolations(final Log log) {
        final String errorProneOutputFilePath = errorProneOutputFilePath(project);

        return violationsFromOutputFile(errorProneOutputFilePath, log);
    }

    private List<Violation> violationsFromOutputFile(final String errorProneOutputFilePath, final Log log) {
        try (final InputStream targetStream = new FileInputStream(errorProneOutputFilePath)) {
            return errorProneErrorLogParser.parse(targetStream);
        } catch (final IOException e) {
            log.warn("No error prone file found. Please register the plugin as a extension");

            return Collections.emptyList();
        }
    }

    private String errorProneOutputFilePath(final MavenProject project) {
        return targetOutputFilePath(project.getBuild().getDirectory(),
            String.format("errorprone-%s.txt", mavenSession.getCurrentProject().getModel().getArtifactId()));
    }

    private static String targetOutputFilePath(final String targetDirectory, final String targetOutputFilename) {
        return targetDirectory + "/" + targetOutputFilename;
    }

    private static class CompilerArgsComposer {
        private static final String ERROR_PRONE_CLASSES_DIR = "error-prone-classes";

        // JEP 396: Strongly encapsulate JDK internals (see Error Prone docs)
        private static final List<String> ERROR_PRONE_EXPORTS = List.of(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
        );

        private static final List<String> ERROR_PRONE_OPENS = List.of(
            "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED"
        );

        public static List<MojoExecutor.Element> compose(final ErrorProneConfiguration errorProneConfiguration, final MavenSession mavenSession) {
            final List<MojoExecutor.Element> args = new ArrayList<>();

            // caller-provided compiler args (first to allow later overrides to win if needed)
            for (String a : errorProneConfiguration.getCompilerArgs()) {
                args.add(arg(a));
            }

            // Due to JEP 396: Strongly Encapsulate JDK Internals by Default - See https://errorprone.info/docs/installation
            ERROR_PRONE_EXPORTS.forEach(f -> args.add(arg("-J" + f)));
            ERROR_PRONE_OPENS.forEach(f -> args.add(arg("-J" + f)));

            // Classpath (ensure latest reactor outputs)
            addClassPathArgs(args, mavenSession);

            // Single-compilation-unit policy for javac
            args.add(arg("-XDcompilePolicy=simple"));

            // Lint config (suppress specific warnings; lombok/JPMS compatibility)
            args.add(arg("-Xlint:all,-serial,-processing,-requires-transitive-automatic,-missing-explicit-ctor,-exports,-requires-automatic"));

            // Error Prone configuration flags
            args.add(arg(String.format(
                ERROR_PRONE_FLAGS_TEMPLATE,
                errorProneConfiguration.getAnnotatedPackages(),
                errorProneConfiguration.getExcludedPaths()
            )));

            return args;
        }

        private static void addClassPathArgs(final List<MojoExecutor.Element> args, final MavenSession session) {
            final MavenProject current = session.getCurrentProject();

            final List<String> rawClasspath;
            try {
                // includes reactor target/classes
                rawClasspath = new ArrayList<>(current.getCompileClasspathElements());
            } catch (final DependencyResolutionRequiredException e) {
                throw new CodeQualityException("Failed to resolve compile classpath", e);
            }

            final List<MavenProject> allProjects = session.getAllProjects();

            // Replace classpath where entries referencing reactor artifacts
            // are swapped for their <buildDirectory>/error-prone-classes
            for (final ListIterator<String> it = rawClasspath.listIterator(); it.hasNext(); ) {
                final String entry = it.next();

                for (final MavenProject mavenProject : allProjects) {
                    final String finalName = (mavenProject.getBuild() != null) ? mavenProject.getBuild().getFinalName() : null;

                    if (finalName != null && entry.contains(finalName)) {
                        final String replacement = Paths.get(mavenProject.getBuild().getDirectory(), ERROR_PRONE_CLASSES_DIR).toString();
                        it.set(replacement);
                        break;
                    }
                }
            }

            final String classpath = String.join(File.pathSeparator, rawClasspath);

            // Because fork=true, we can override the classpath passed to external javac
            args.add(arg("-cp"));
            args.add(arg(classpath));
        }

        private static MojoExecutor.Element arg(final String value) {
            return element(MojoExecutor.name("arg"), value);
        }
    }
}
