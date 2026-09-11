package io.github.finoid.maven.plugins.codequality.step;

import io.github.finoid.maven.plugins.codequality.ExecutionContext;
import io.github.finoid.maven.plugins.codequality.archunit.ArchRuleResolver;
import io.github.finoid.maven.plugins.codequality.archunit.ArchUnitAnalyzer;
import io.github.finoid.maven.plugins.codequality.archunit.NamedArchRule;
import io.github.finoid.maven.plugins.codequality.archunit.TestClassPathResolver;
import io.github.finoid.maven.plugins.codequality.configuration.ArchUnitConfiguration;
import io.github.finoid.maven.plugins.codequality.configuration.CodeQualityConfiguration;
import io.github.finoid.maven.plugins.codequality.exceptions.CodeQualityException;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ArchRuleResolver archRuleResolver;
    private final ArchUnitAnalyzer archUnitAnalyzer;
    private final TestClassPathResolver testClassPathResolver;

    @Inject
    public ArchUnitStep(final ArchRuleResolver archRuleResolver, final ArchUnitAnalyzer archUnitAnalyzer,
                        final TestClassPathResolver testClassPathResolver) {
        this.archRuleResolver = Precondition.nonNull(archRuleResolver, "ArchRuleResolver shouldn't be null");
        this.archUnitAnalyzer = Precondition.nonNull(archUnitAnalyzer, "ArchUnitAnalyzer shouldn't be null");
        this.testClassPathResolver = Precondition.nonNull(testClassPathResolver, "TestClassPathResolver shouldn't be null");
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
        // Nothing is written between runs: the classes are re-imported and the rules re-evaluated on every execution.
        return CleanContext.DO_NOTHING;
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

            return archUnitAnalyzer.analyze(rules, configuration, context);
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
