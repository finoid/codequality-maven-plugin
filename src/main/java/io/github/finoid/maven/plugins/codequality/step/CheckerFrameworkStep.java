package io.github.finoid.maven.plugins.codequality.step;

import io.github.finoid.maven.plugins.codequality.MavenAnnotationProcessorsManager;
import io.github.finoid.maven.plugins.codequality.configuration.CheckerFrameworkConfiguration;
import io.github.finoid.maven.plugins.codequality.configuration.CodeQualityConfiguration;
import io.github.finoid.maven.plugins.codequality.exceptions.CodeQualityException;
import io.github.finoid.maven.plugins.codequality.report.CheckerFrameworkViolationLogParser;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.util.CollectorUtils;
import io.github.finoid.maven.plugins.codequality.util.MojoUtils.ElementUtils;
import io.github.finoid.maven.plugins.codequality.util.MojoUtils.PluginUtils;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import io.github.finoid.maven.plugins.codequality.util.ProjectUtils;
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
import java.util.Set;

import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;

/**
 * Step which executes the CheckerFrameworkStep analyzer.
 */
@Singleton
public class CheckerFrameworkStep implements Step<CheckerFrameworkConfiguration> {
    private final MavenProject project;
    private final MavenSession mavenSession;
    private final BuildPluginManager pluginManager;
    private final CheckerFrameworkViolationLogParser checkerFrameworkViolationLogParser;

    @Inject
    public CheckerFrameworkStep(
        final MavenProject project,
        final MavenSession mavenSession,
        final BuildPluginManager pluginManager,
        final CheckerFrameworkViolationLogParser checkerFrameworkViolationLogParser
    ) {
        this.project = Precondition.nonNull(project, "MavenProject shouldn't be null");
        this.mavenSession = Precondition.nonNull(mavenSession, "MavenSession shouldn't be null");
        this.pluginManager = Precondition.nonNull(pluginManager, "BuildPluginManager shouldn't be null");
        this.checkerFrameworkViolationLogParser =
            Precondition.nonNull(checkerFrameworkViolationLogParser, "CheckerFrameworkViolationLogParser shouldn't be null");
    }

    @Override
    public boolean isEnabled(final CheckerFrameworkConfiguration configuration) {
        return configuration.isEnabled();
    }

    @Override
    public PrerequisiteResult hasPrerequisites(final CheckerFrameworkConfiguration configuration) {
        if (ProjectUtils.isPresentOnClassPath(mavenSession.getCurrentProject(), "org.checkerframework", "checker-qual")) {
            return PrerequisiteResult.OK;
        }

        return PrerequisiteResult.notOK("org.checkerframework.checker-qual is missing on the class path");
    }

    @Override
    public StepType type() {
        return StepType.CHECKER_FRAMEWORK;
    }

    @Override
    public StepResult execute(final CodeQualityConfiguration codeQualityConfiguration, final CheckerFrameworkConfiguration checkerFrameworkConfiguration,
                              final Log log) {
        final List<Violation> violations = executeStep(codeQualityConfiguration, checkerFrameworkConfiguration, log);

        return StepResult.create(StepType.CHECKER_FRAMEWORK, checkerFrameworkConfiguration.isPermissive(), violations);
    }

    @Override
    public CleanContext getCleanContext() {
        return CleanContext.DO_NOTHING;
    }

    private List<Violation> executeStep(
        final CodeQualityConfiguration codeQualityConfiguration,
        final CheckerFrameworkConfiguration stepConfiguration,
        final Log log
    ) {
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
                    element("source", javaVersion),
                    element("target", javaVersion),
                    element("release", javaVersion),
                    element("outputDirectory", currentProject.getBuild().getDirectory() + "/checker-framework-classes"),
                    element("failOnError", "false"),
                    element("showWarnings", "true"),
                    element(MojoExecutor.name("compilerArgs"), elementsOfCompilerArgs(stepConfiguration)
                        .toArray(MojoExecutor.Element[]::new)),
                    element(MojoExecutor.name("annotationProcessorPaths"),
                        elementsOfAnnotationProcessorPaths(currentProject, codeQualityConfiguration, stepConfiguration)
                            .toArray(MojoExecutor.Element[]::new)),
                    element(MojoExecutor.name("annotationProcessors"),
                        elementsOfCheckers(currentProject, stepConfiguration.getCheckers(), codeQualityConfiguration)
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
            throw new CodeQualityException("Error during execution of CheckerFramework step", e);
        }
    }

    private List<MojoExecutor.Element> elementsOfCompilerArgs(final CheckerFrameworkConfiguration checkerFrameworkConfiguration) {
        return CompilerArgsComposer.compose(checkerFrameworkConfiguration, mavenSession);
    }

    private List<MojoExecutor.Element> elementsOfAnnotationProcessorPaths(final MavenProject currentProject,
                                                                          final CodeQualityConfiguration codeQualityConfiguration,
                                                                          final CheckerFrameworkConfiguration checkerFrameworkConfiguration) {
        final MavenAnnotationProcessorsManager annotationProcessorsManager = new MavenAnnotationProcessorsManager(currentProject, codeQualityConfiguration);

        final List<MojoExecutor.Element> annotationProcessorPaths = annotationProcessorsManager.annotationPaths().stream()
            .map(it -> ElementUtils.annotationProcessor(it.getGroupId(), it.getArtifactId(), it.getVersion()))
            .collect(CollectorUtils.toMutableList());

        annotationProcessorPaths.add(
            ElementUtils.annotationProcessor("org.checkerframework", "checker", checkerFrameworkConfiguration.getVersions().getCheckerFramework()));

        return annotationProcessorPaths;
    }

    private List<MojoExecutor.Element> elementsOfCheckers(final MavenProject currentProject, final Set<String> checkers,
                                                          final CodeQualityConfiguration codeQualityConfiguration) {
        final MavenAnnotationProcessorsManager annotationProcessorsManager = new MavenAnnotationProcessorsManager(currentProject, codeQualityConfiguration);

        return annotationProcessorsManager.annotationProcessors(checkers).stream()
            .map(annotationProcessor -> element(MojoExecutor.name("annotationProcessor"), annotationProcessor))
            .toList();
    }

    private List<Violation> parseViolations(final Log log) {
        final String checkerFrameworkOutputFilePath = checkerFrameworkOutputFilePath(project);

        return violationsFromOutputFile(checkerFrameworkOutputFilePath, log);
    }

    private List<Violation> violationsFromOutputFile(final String checkerFrameworkOutputFilePath, final Log log) {
        try (final InputStream targetStream = new FileInputStream(checkerFrameworkOutputFilePath)) {
            return checkerFrameworkViolationLogParser.parse(targetStream);
        } catch (final IOException e) {
            log.warn("No checker framework file found. Please register the plugin as a extension");

            return Collections.emptyList();
        }
    }

    private String checkerFrameworkOutputFilePath(final MavenProject project) {
        return targetOutputFilePath(project.getBuild().getDirectory(),
            String.format("checkerframework-%s.txt", mavenSession.getCurrentProject().getModel().getArtifactId()));
    }

    private static String targetOutputFilePath(final String targetDirectory, final String targetOutputFilename) {
        return targetDirectory + "/" + targetOutputFilename;
    }

    private static class CompilerArgsComposer {
        private static final String CHECKER_FRAMEWORK_CLASSES_DIR = "checker-framework-classes";

        // JEP 396: Strongly encapsulate JDK internals (see Checker Framework docs)
        private static final List<String> CHECKER_FRAMEWORK_EXPORTS = List.of(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
        );

        private static final List<String> CHECKER_FRAMEWORK_OPENS = List.of(
            "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED"
        );

        public static List<MojoExecutor.Element> compose(final CheckerFrameworkConfiguration checkerFrameworkConfiguration, final MavenSession mavenSession) {
            final List<MojoExecutor.Element> args = new ArrayList<>();

            // caller-provided compiler args (first to allow later overrides to win if needed)
            for (String a : checkerFrameworkConfiguration.getCompilerArgs()) {
                args.add(arg(a));
            }

            // Due to JEP 396: Strongly Encapsulate JDK Internals by Default - See https://errorprone.info/docs/installation
            CHECKER_FRAMEWORK_EXPORTS.forEach(f -> args.add(arg("-J" + f)));
            CHECKER_FRAMEWORK_OPENS.forEach(f -> args.add(arg("-J" + f)));

            // Classpath (ensure latest reactor outputs)
            addClassPathArgs(args, mavenSession);

            // Checker framework rules that are suppressed by default
            args.add(element(MojoExecutor.name("arg"),
                "-AsuppressWarnings=type.anno.before.decl.anno,type.anno.before.modifier")); // TODO (nw) should be configurable

            // Output errors as warnings
            args.add(element(MojoExecutor.name("arg"), "-Awarns"));

            // The -processing suppress "No processor claimed any of these annotations"
            // Suppress warnings related to JPMS due to compatibility issues with lombok
            args.add(element(MojoExecutor.name("arg"),
                "-Xlint:all,-serial,-processing,-requires-transitive-automatic,-missing-explicit-ctor,-exports,-requires-automatic"));

            // Skip target directory which includes generated sources
            args.add(element(MojoExecutor.name("arg"), "-AskipFiles=/target/"));

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
                        final String replacement = Paths.get(mavenProject.getBuild().getDirectory(), CHECKER_FRAMEWORK_CLASSES_DIR).toString();
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
