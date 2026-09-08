package io.github.finoid.maven.plugins.codequality.step;

import io.github.finoid.maven.plugins.codequality.ExecutionContext;
import io.github.finoid.maven.plugins.codequality.MavenAnnotationProcessorsManager;
import io.github.finoid.maven.plugins.codequality.configuration.CheckerFrameworkConfiguration;
import io.github.finoid.maven.plugins.codequality.configuration.CodeQualityConfiguration;
import io.github.finoid.maven.plugins.codequality.exceptions.CodeQualityException;
import io.github.finoid.maven.plugins.codequality.report.CheckerFrameworkViolationLogParser;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.util.CollectorUtils;
import io.github.finoid.maven.plugins.codequality.util.ExceptionUtils;
import io.github.finoid.maven.plugins.codequality.util.MojoUtils.ElementUtils;
import io.github.finoid.maven.plugins.codequality.util.MojoUtils.PluginUtils;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import io.github.finoid.maven.plugins.codequality.util.ProjectUtils;
import io.github.finoid.maven.plugins.codequality.util.PropertyUtils;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
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
    /**
     * The root session of the build. Only used for reactor wide state, which is shared by - and identical for - every
     * builder thread. The module currently being analyzed is taken from the {@link ExecutionContext} instead, see
     * {@link ExecutionContext} for why.
     */
    private final MavenSession mavenSession;
    private final BuildPluginManager pluginManager;
    private final CheckerFrameworkViolationLogParser checkerFrameworkViolationLogParser;

    @Inject
    public CheckerFrameworkStep(
        final MavenSession mavenSession,
        final BuildPluginManager pluginManager,
        final CheckerFrameworkViolationLogParser checkerFrameworkViolationLogParser
    ) {
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
    public PrerequisiteResult hasPrerequisites(final CheckerFrameworkConfiguration configuration, final ExecutionContext context) {
        if (ProjectUtils.isPresentOnClassPath(context.getProject(), "org.checkerframework", "checker-qual")) {
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
                              final ExecutionContext context) {
        final List<Violation> violations = executeStep(codeQualityConfiguration, checkerFrameworkConfiguration, context);

        return StepResult.create(StepType.CHECKER_FRAMEWORK, checkerFrameworkConfiguration.isPermissive(), violations);
    }

    @Override
    public CleanContext getCleanContext() {
        return CleanContext.DO_NOTHING;
    }

    private List<Violation> executeStep(
        final CodeQualityConfiguration codeQualityConfiguration,
        final CheckerFrameworkConfiguration stepConfiguration,
        final ExecutionContext context
    ) {
        final PluginDescriptor descriptor =
            PluginUtils.pluginDescriptor("org.apache.maven.plugins", "maven-compiler-plugin", codeQualityConfiguration.getVersions().getMavenCompiler());

        final MavenProject currentProject = context.getProject();

        final String javaVersion = PropertyUtils.valueOrFallback(currentProject.getProperties(), "java.version", "21");

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
                    element("failOnError", "true"),
                    element("showWarnings", "true"),
                    element(MojoExecutor.name("compilerArgs"), elementsOfCompilerArgs(stepConfiguration, currentProject)
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

            return parseViolations(context);
        } catch (final Exception e) {
            // The forked compiler reports through its own log, which the plugin redirects to a file, so the reason a
            // step failed is regularly only in that file. Both the file and the deepest cause are named here, the
            // wrapping exceptions of a forked mojo say little on their own.
            throw new CodeQualityException(String.format(
                "Error during execution of CheckerFramework step. Cause: %s. The output of the forked compiler was captured in %s",
                ExceptionUtils.rootCauseMessage(e), checkerFrameworkOutputFilePath(currentProject)), e);
        }
    }

    private List<MojoExecutor.Element> elementsOfCompilerArgs(final CheckerFrameworkConfiguration checkerFrameworkConfiguration,
                                                              final MavenProject currentProject) {
        return CompilerArgsComposer.compose(checkerFrameworkConfiguration, currentProject, mavenSession);
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

    private List<Violation> parseViolations(final ExecutionContext context) {
        final String checkerFrameworkOutputFilePath = checkerFrameworkOutputFilePath(context.getProject());

        return violationsFromOutputFile(checkerFrameworkOutputFilePath, context);
    }

    private List<Violation> violationsFromOutputFile(final String checkerFrameworkOutputFilePath, final ExecutionContext context) {
        try (final InputStream targetStream = new FileInputStream(checkerFrameworkOutputFilePath)) {
            return checkerFrameworkViolationLogParser.parse(targetStream);
        } catch (final IOException e) {
            context.getLog().warn("No checker framework file found. Please register the plugin as an extension");

            return Collections.emptyList();
        }
    }

    private static String checkerFrameworkOutputFilePath(final MavenProject project) {
        return targetOutputFilePath(project.getBuild().getDirectory(),
            String.format("checkerframework-%s.txt", project.getModel().getArtifactId()));
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

        private static List<MojoExecutor.Element> compose(final CheckerFrameworkConfiguration checkerFrameworkConfiguration,
                                                          final MavenProject currentProject, final MavenSession mavenSession) {
            final List<MojoExecutor.Element> args = new ArrayList<>();

            // caller-provided compiler args (first to allow later overrides to win if needed)
            for (String a : checkerFrameworkConfiguration.getCompilerArgs()) {
                args.add(arg(a));
            }

            // Due to JEP 396: Strongly Encapsulate JDK Internals by Default - See https://errorprone.info/docs/installation
            CHECKER_FRAMEWORK_EXPORTS.forEach(f -> args.add(arg("-J" + f)));
            CHECKER_FRAMEWORK_OPENS.forEach(f -> args.add(arg("-J" + f)));

            // Classpath (ensure latest reactor outputs)
            addClassPathArgs(args, currentProject, mavenSession);

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

        private static void addClassPathArgs(final List<MojoExecutor.Element> args, final MavenProject current, final MavenSession session) {
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

                    final String artifactNameAndVersion = mavenProject.getArtifact().getArtifactId() + "-" + mavenProject.getArtifact().getVersion() + ".jar";

                    if (finalName != null && entry.contains(artifactNameAndVersion)) {
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
