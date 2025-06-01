package io.github.finoid.maven.plugins.codequality.step;

import io.github.finoid.maven.plugins.codequality.configuration.CheckstyleConfiguration;
import io.github.finoid.maven.plugins.codequality.configuration.CodeQualityConfiguration;
import io.github.finoid.maven.plugins.codequality.exceptions.CodeQualityException;
import io.github.finoid.maven.plugins.codequality.factories.CheckstyleExecutorRequestFactory;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import lombok.SneakyThrows;
import org.apache.maven.plugin.logging.Log;
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
    public StepResult execute(final CodeQualityConfiguration codeQualityConfiguration, final CheckstyleConfiguration stepConfiguration, final Log log) {
        try {
            final StepResult resultMain = executeForEnvironment(stepConfiguration, stepConfiguration.getExecutionMain(), log);
            final StepResult resultTest = executeForEnvironment(stepConfiguration, stepConfiguration.getExecutionTest(), log);

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
        final Log log
    ) {
        return executeCheckstyle(configuration, executionEnvironment, log);
    }

    @SneakyThrows
    private StepResult executeCheckstyle(final CheckstyleConfiguration configuration, final CheckstyleConfiguration.ExecutionEnvironment executionEnvironment,
                                         final Log log) {
        if (!executionEnvironment.isEnabled()) {
            log.info("Skipping Checkstyle Sub Step for " + executionEnvironment);

            return StepResult.create(StepType.CHECKSTYLE, configuration.isPermissive(), Collections.emptyList());
        }

        log.info("Executing Checkstyle Sub Step for " + executionEnvironment.getEnvironment());

        final CheckstyleExecutorRequest request = checkstyleExecutorRequestFactory.create(configuration, executionEnvironment, log);

        final CheckstyleResults checkstyleResults = checkstyleExecutor.executeCheckstyle(request);

        final List<Violation> violations = checkstyleResults.getFiles()
            .entrySet()
            .stream()
            .flatMap(it -> it.getValue().stream())
            .map(violationConverter::ofAuditEvent)
            .toList();

        return StepResult.create(StepType.CHECKSTYLE, configuration.isPermissive(), violations);
    }
}
