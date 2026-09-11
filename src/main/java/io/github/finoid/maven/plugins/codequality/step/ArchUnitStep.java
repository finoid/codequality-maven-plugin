package io.github.finoid.maven.plugins.codequality.step;

import io.github.finoid.maven.plugins.codequality.ExecutionContext;
import io.github.finoid.maven.plugins.codequality.MavenAnnotationProcessorsManager;
import io.github.finoid.maven.plugins.codequality.archunit.ArchRuleResolver;
import io.github.finoid.maven.plugins.codequality.archunit.ArchUnitAnalyzer;
import io.github.finoid.maven.plugins.codequality.archunit.NamedArchRule;
import io.github.finoid.maven.plugins.codequality.archunit.TestClassPathResolver;
import io.github.finoid.maven.plugins.codequality.configuration.ArchUnitConfiguration;
import io.github.finoid.maven.plugins.codequality.configuration.CodeQualityConfiguration;
import io.github.finoid.maven.plugins.codequality.exceptions.CodeQualityException;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.util.CollectorUtils;
import io.github.finoid.maven.plugins.codequality.util.MojoUtils.ElementUtils;
import io.github.finoid.maven.plugins.codequality.util.MojoUtils.PluginUtils;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import io.github.finoid.maven.plugins.codequality.util.PropertyUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.twdata.maven.mojoexecutor.MojoExecutor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;

/**
 * Step which evaluates ArchUnit rules against the compiled classes of the module.
 * <p>
 * Unlike the other analyzers, the checks this step runs are not built in: the rules come from the project, either
 * referenced explicitly through {@code archUnit.rules} or discovered from the test classpath through the service
 * loader. The step is therefore a no-op until a project configures at least one of the two.
 * <p>
 * Running the rules here rather than as {@code @ArchTest} JUnit tests means they also run when the build skips tests,
 * and that their findings land in the same report as the other analyzers. A project which keeps its ArchUnit tests
 * should be aware the rules are then evaluated twice, once by surefire and once here.
 */
@Singleton
public class ArchUnitStep implements Step<ArchUnitConfiguration> {
    private static final String ARCH_UNIT_CLASSES = "archunit-classes";

    private final ArchRuleResolver archRuleResolver;
    private final ArchUnitAnalyzer archUnitAnalyzer;
    private final TestClassPathResolver testClassPathResolver;
    private final CodeQualityConfiguration codeQualityConfiguration;
    private final MavenSession mavenSession;
    private final BuildPluginManager pluginManager;

    @Inject
    public ArchUnitStep(final ArchRuleResolver archRuleResolver, final ArchUnitAnalyzer archUnitAnalyzer,
                        final TestClassPathResolver testClassPathResolver, final CodeQualityConfiguration codeQualityConfiguration,
                        final MavenSession mavenSession, final BuildPluginManager pluginManager) {
        this.archRuleResolver = Precondition.nonNull(archRuleResolver, "ArchRuleResolver shouldn't be null");
        this.archUnitAnalyzer = Precondition.nonNull(archUnitAnalyzer, "ArchUnitAnalyzer shouldn't be null");
        this.testClassPathResolver = Precondition.nonNull(testClassPathResolver, "TestClassPathResolver shouldn't be null");
        this.codeQualityConfiguration = Precondition.nonNull(codeQualityConfiguration, "CodeQualityConfiguration shouldn't be null");
        this.mavenSession = Precondition.nonNull(mavenSession, "MavenSession shouldn't be null");
        this.pluginManager = Precondition.nonNull(pluginManager, "BuildPluginManager shouldn't be null");
    }

    @Override
    public boolean isEnabled(final ArchUnitConfiguration configuration) {
        return configuration.isEnabled();
    }

    @Override
    public PrerequisiteResult hasPrerequisites(final ArchUnitConfiguration configuration, final ExecutionContext context) {
        if (!configuration.isServiceLoaderEnabled() && configuration.getRules().isEmpty()) {
            return PrerequisiteResult.notOK("no rules are configured and the service loader is disabled");
        }

        /*
         * Unlike the analyzers which fork a compiler of their own, this step reads the classes the build has already
         * produced. Bound to a phase before compile - or invoked directly on the command line ahead of one - there is
         * nothing to read, and every rule would pass for the wrong reason. Reported as a missing prerequisite rather
         * than as an empty result, so a run which cannot find anything never looks like a clean one.
         */
        if (!configuration.isCompileIfMissing() && !Files.isDirectory(outputDirectoryOf(context.getProject()))) {
            return PrerequisiteResult.notOK(
                "the module has no compiled classes, the goal has to run at or after the compile phase."
                    + " Set archUnit.compileIfMissing to compile it instead");
        }

        return PrerequisiteResult.OK;
    }

    @Override
    public StepType type() {
        return StepType.ARCH_UNIT;
    }

    @Override
    public StepResult execute(final CodeQualityConfiguration codeQualityConfiguration, final ArchUnitConfiguration stepConfiguration,
                              final ExecutionContext context) {
        return StepResult.create(StepType.ARCH_UNIT, stepConfiguration.isPermissive(), executeStep(stepConfiguration, context));
    }

    @Override
    public CleanContext getCleanContext() {
        /*
         * Only the output of an own compilation is cleaned, and only that. The classes of the build itself are left
         * alone, but a stale class of ours - from a source file since deleted or renamed - would otherwise be
         * analyzed forever, which is the sort of finding nobody can explain.
         */
        return new CleanContext(CleanContext.CleanType.DIRECTORY, ARCH_UNIT_CLASSES, "**/*");
    }

    private List<Violation> executeStep(final ArchUnitConfiguration configuration, final ExecutionContext context) {
        try (URLClassLoader classLoader = testClassLoaderOf(context.getProject())) {
            final List<NamedArchRule> rules = archRuleResolver.resolve(configuration, classLoader, context);

            if (rules.isEmpty()) {
                context.getLog()
                    .info("No ArchUnit rules were resolved. Skipping...");

                return List.of();
            }

            context.getLog()
                .debug(String.format("Evaluating %d ArchUnit rule(s): %s", rules.size(), rules.stream().map(NamedArchRule::name).toList()));

            warnOnUnmatchedSeverities(configuration, rules, context);

            return archUnitAnalyzer.analyze(classDirectoriesOf(configuration, context), rules, configuration, context);
        } catch (final IOException e) {
            throw new CodeQualityException(String.format("Failed to close the ArchUnit class loader. Cause: %s", e.getMessage()), e);
        }
    }

    /**
     * Warns about severity overrides which match no resolved rule.
     * <p>
     * An override is keyed by rule name, and getting that name wrong is silent otherwise: the rule simply keeps the
     * default severity, which looks exactly like the override having been applied to a rule that reports nothing.
     */
    private void warnOnUnmatchedSeverities(final ArchUnitConfiguration configuration, final List<NamedArchRule> rules,
                                           final ExecutionContext context) {
        final Set<String> resolvedNames = rules.stream()
            .map(NamedArchRule::name)
            .collect(Collectors.toSet());

        configuration.getRuleSeverities()
            .keySet()
            .stream()
            .filter(name -> !resolvedNames.contains(name))
            .forEach(name -> context.getLog()
                .warn(String.format("ArchUnit severity override [%s] matches no resolved rule. Known rules: %s", name, resolvedNames)));
    }

    /**
     * The directories holding the classes to analyze.
     * <p>
     * Ordinarily the output of the build. When the module has not been compiled and {@code compileIfMissing} is set,
     * a compilation of its own is run first, into a directory of its own so that neither the output of the build is
     * overwritten nor a later phase led to believe the module is already built.
     */
    private List<Path> classDirectoriesOf(final ArchUnitConfiguration configuration, final ExecutionContext context) {
        final MavenProject project = context.getProject();

        final List<Path> directories = new ArrayList<>();

        if (Files.isDirectory(outputDirectoryOf(project))) {
            directories.add(outputDirectoryOf(project));
        } else if (configuration.isCompileIfMissing()) {
            directories.add(compile(context));
        }

        if (configuration.isAnalyzeTestClasses() && Files.isDirectory(testOutputDirectoryOf(project))) {
            directories.add(testOutputDirectoryOf(project));
        }

        return directories;
    }

    /**
     * Compiles the main sources of the module into {@code target/archunit-classes}.
     *
     * <p>The release level and the annotation processors of the module are reproduced, which covers the common case
     * of a Lombok using service. A module with a bespoke compiler configuration - additional compiler arguments,
     * generated source roots, a module path - is not fully reproduced, so what is analyzed can differ from what the
     * build itself produces. Running the goal after the compile phase avoids the question entirely.
     */
    private Path compile(final ExecutionContext context) {
        final MavenProject project = context.getProject();

        final PluginDescriptor descriptor = PluginUtils.pluginDescriptor("org.apache.maven.plugins", "maven-compiler-plugin",
            codeQualityConfiguration.getVersions().getMavenCompiler());

        final String javaVersion = PropertyUtils.valueOrFallback(project.getProperties(), "java.version", "21");
        final Path outputDirectory = Path.of(project.getBuild().getDirectory(), ARCH_UNIT_CLASSES);

        // The forked compile assigns an artifact file to the project, which would later be reported as
        // 'The packaging for this project did not assign a file to the build artifact.'
        final File originalArtifactFile = project.getArtifact()
            .getFile();

        context.getLog()
            .info(String.format("Compiling %s for ArchUnit, no compiled classes were found", project.getArtifactId()));

        try {
            executeMojo(
                PluginUtils.pluginOfDescriptor(descriptor),
                goal("compile"),
                configuration(
                    element(MojoExecutor.name("source"), javaVersion),
                    element(MojoExecutor.name("target"), javaVersion),
                    element(MojoExecutor.name("release"), javaVersion),
                    element(MojoExecutor.name("outputDirectory"), outputDirectory.toString()),
                    element(MojoExecutor.name("annotationProcessorPaths"), annotationProcessorPathsOf(project)
                        .toArray(MojoExecutor.Element[]::new))
                ),
                executionEnvironment(project, mavenSession, pluginManager));

            return outputDirectory;
        } catch (final MojoExecutionException e) {
            throw new CodeQualityException(
                String.format("Failed to compile module [%s] for ArchUnit. Cause: %s", project.getArtifactId(), e.getMessage()), e);
        } finally {
            project.getArtifact()
                .setFile(originalArtifactFile);
        }
    }

    private List<MojoExecutor.Element> annotationProcessorPathsOf(final MavenProject project) {
        return new MavenAnnotationProcessorsManager(project, codeQualityConfiguration).annotationPaths()
            .stream()
            .map(path -> ElementUtils.annotationProcessor(path.getGroupId(), path.getArtifactId(), path.getVersion()))
            .collect(CollectorUtils.toMutableList());
    }

    private static Path outputDirectoryOf(final MavenProject project) {
        return Path.of(project.getBuild().getOutputDirectory());
    }

    private static Path testOutputDirectoryOf(final MavenProject project) {
        return Path.of(project.getBuild().getTestOutputDirectory());
    }

    /**
     * A class loader over the test classpath of the module, delegating to the class loader of this plugin.
     * <p>
     * Parent first delegation is deliberate: ArchUnit must resolve to the copy the plugin was built against, so that
     * the {@code ArchRule} instances the project hands back are of the type this plugin evaluates. A project holding
     * a second copy on its own classpath would otherwise produce a {@code ClassCastException} which is hard to read.
     * <p>
     * Delegating to the plugin's own class loader also means a rule library declared as a dependency of the plugin
     * itself is found, without the analyzed project depending on it at all.
     */
    private URLClassLoader testClassLoaderOf(final MavenProject project) {
        final List<URL> classPath = testClassPathResolver.resolve(project);

        return new URLClassLoader(classPath.toArray(URL[]::new), getClass().getClassLoader());
    }
}
