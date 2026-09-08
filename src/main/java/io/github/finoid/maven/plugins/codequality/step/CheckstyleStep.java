package io.github.finoid.maven.plugins.codequality.step;

import io.github.finoid.maven.plugins.codequality.ExecutionContext;
import io.github.finoid.maven.plugins.codequality.configuration.CheckstyleConfiguration;
import io.github.finoid.maven.plugins.codequality.configuration.CodeQualityConfiguration;
import io.github.finoid.maven.plugins.codequality.exceptions.CodeQualityException;
import io.github.finoid.maven.plugins.codequality.factories.CheckstyleExecutorRequestFactory;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import lombok.SneakyThrows;
import org.apache.maven.plugins.checkstyle.exec.CheckstyleExecutor;
import org.apache.maven.plugins.checkstyle.exec.CheckstyleExecutorRequest;
import org.apache.maven.plugins.checkstyle.exec.CheckstyleResults;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

/**
 * Step which executes the Checkstyle analyzer.
 */
@Singleton
public class CheckstyleStep implements Step<CheckstyleConfiguration> {
    private final CheckstyleExecutor checkstyleExecutor;
    private final CheckstyleExecutorRequestFactory checkstyleExecutorRequestFactory;
    private final ViolationConverter violationConverter;

    @Inject
    public CheckstyleStep(
        final CheckstyleExecutor checkstyleExecutor,
        final CheckstyleExecutorRequestFactory checkstyleExecutorRequestFactory,
        final ViolationConverter violationConverter
    ) {
        this.checkstyleExecutor = Precondition.nonNull(checkstyleExecutor, "CheckstyleExecutor shouldn't be null");
        this.checkstyleExecutorRequestFactory = Precondition.nonNull(checkstyleExecutorRequestFactory, "CheckstyleExecutorRequestFactory shouldn't be null");
        this.violationConverter = Precondition.nonNull(violationConverter, "ViolationConverter shouldn't be null");
    }

    @Override
    public boolean isEnabled(final CheckstyleConfiguration configuration) {
        return configuration.isEnabled();
    }

    @Override
    public StepType type() {
        return StepType.CHECKSTYLE;
    }

    @Override
    public StepResult execute(final CodeQualityConfiguration codeQualityConfiguration, final CheckstyleConfiguration stepConfiguration,
                              final ExecutionContext context) {
        try {
            final StepResult resultMain = executeForEnvironment(stepConfiguration, stepConfiguration.getExecutionMain(), context);
            final StepResult resultTest = executeForEnvironment(stepConfiguration, stepConfiguration.getExecutionTest(), context);

            return StepResult.create(StepType.CHECKSTYLE, stepConfiguration.isPermissive(), resultMain.getViolations(), resultTest.getViolations());
        } catch (final Exception e) {
            throw new CodeQualityException("Error during execution of checkstyle step", e);
        }
    }

    @Override
    public CleanContext getCleanContext() {
        return CleanContext.DO_NOTHING;
    }

    private StepResult executeForEnvironment(
        final CheckstyleConfiguration configuration,
        final CheckstyleConfiguration.ExecutionEnvironment executionEnvironment,
        final ExecutionContext context
    ) {
        return executeCheckstyle(configuration, executionEnvironment, context);
    }

    @SneakyThrows
    private StepResult executeCheckstyle(final CheckstyleConfiguration configuration, final CheckstyleConfiguration.ExecutionEnvironment executionEnvironment,
                                         final ExecutionContext context) {
        if (!executionEnvironment.isEnabled()) {
            context.getLog().info("Skipping Checkstyle Sub Step for " + executionEnvironment);

            return StepResult.create(StepType.CHECKSTYLE, configuration.isPermissive(), Collections.emptyList());
        }

        context.getLog().info("Executing Checkstyle Sub Step for " + executionEnvironment.getEnvironment());

        final CheckstyleExecutorRequest request = checkstyleExecutorRequestFactory.create(configuration, executionEnvironment, context);

        final CheckstyleResults checkstyleResults = executeCheckstyle(request);

        final List<Violation> violations = checkstyleResults.getFiles()
            .entrySet()
            .stream()
            .flatMap(it -> it.getValue().stream())
            .map(violationConverter::ofAuditEvent)
            .toList();

        return StepResult.create(StepType.CHECKSTYLE, configuration.isPermissive(), violations);
    }

    /**
     * Runs Checkstyle for a single module.
     * <p>
     * {@code DefaultCheckstyleExecutor} is a singleton which reconfigures a shared {@code ResourceManager} - the
     * output directory and the search paths used to resolve the configuration, header and suppression files - for
     * every request it is given. Concurrent requests would therefore resolve each other's resources, so the executor
     * is used by one module at a time. Only the Checkstyle analysis itself is serialized, the compiler based steps of
     * the other modules keep running in parallel.
     */
    private CheckstyleResults executeCheckstyle(final CheckstyleExecutorRequest request) throws Exception {
        synchronized (checkstyleExecutor) {
            return checkstyleExecutor.executeCheckstyle(request);
        }
    }
}
