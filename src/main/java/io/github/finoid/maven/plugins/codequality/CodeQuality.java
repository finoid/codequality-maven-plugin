package io.github.finoid.maven.plugins.codequality;

import io.github.finoid.maven.plugins.codequality.configuration.CodeQualityConfiguration;
import io.github.finoid.maven.plugins.codequality.configuration.Configuration;
import io.github.finoid.maven.plugins.codequality.exceptions.SeverityThresholdException;
import io.github.finoid.maven.plugins.codequality.exceptions.StepExecutionException;
import io.github.finoid.maven.plugins.codequality.handlers.CleanHandler;
import io.github.finoid.maven.plugins.codequality.report.Severity;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.step.CheckerFrameworkStep;
import io.github.finoid.maven.plugins.codequality.step.CheckstyleStep;
import io.github.finoid.maven.plugins.codequality.step.ErrorProneStep;
import io.github.finoid.maven.plugins.codequality.step.ProjectStepResults;
import io.github.finoid.maven.plugins.codequality.step.Step;
import io.github.finoid.maven.plugins.codequality.step.StepResult;
import io.github.finoid.maven.plugins.codequality.step.StepResults;
import io.github.finoid.maven.plugins.codequality.storage.StepResultsRepository;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import io.github.finoid.maven.plugins.codequality.util.ProjectUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

@Mojo(name = "code-quality", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.COMPILE)
public class CodeQuality extends AbstractMojo {
    private final CheckstyleStep checkstyleStep;
    private final ErrorProneStep errorProneStep;
    private final CheckerFrameworkStep checkerFrameworkStep;
    private final CleanHandler cleanHandler;
    private final MavenSession mavenSession;
    private final StepResultsRepository stepResultsRepository;
    private final DiffCoverageApplier diffCoverageApplier;
    private final List<ViolationReporter> violationReporters;

    @Parameter(alias = "codeQuality")
    private CodeQualityConfiguration codeQualityConfiguration;

    @Inject
    public CodeQuality(
        final CheckstyleStep checkstyleStep,
        final ErrorProneStep errorProneStep,
        final CheckerFrameworkStep checkerFrameworkStep,
        final CleanHandler cleanHandler,
        final MavenSession mavenSession,
        final StepResultsRepository stepResultsRepository,
        final DiffCoverageApplier diffCoverageApplier,
        final List<ViolationReporter> violationReporters,
        final CodeQualityConfiguration codeQualityConfiguration
    ) {
        this.checkstyleStep = Precondition.nonNull(checkstyleStep, "CheckstyleStep shouldn't be null");
        this.errorProneStep = Precondition.nonNull(errorProneStep, "ErrorProneStep shouldn't be null");
        this.checkerFrameworkStep = Precondition.nonNull(checkerFrameworkStep, "CheckerFrameworkStep shouldn't be null");
        this.cleanHandler = Precondition.nonNull(cleanHandler, "CleanHandler shouldn't be null");
        this.mavenSession = Precondition.nonNull(mavenSession, "MavenSession shouldn't be null");
        this.stepResultsRepository = Precondition.nonNull(stepResultsRepository, "StepResultsRepository shouldn't be null");
        this.diffCoverageApplier = Precondition.nonNull(diffCoverageApplier, "DiffCoverageApplier shouldn't be null");
        this.violationReporters = Precondition.nonNull(violationReporters, "ViolationResultLogOutput shouldn't be null");
        this.codeQualityConfiguration = Precondition.nonNull(codeQualityConfiguration, "CodeQualityConfiguration shouldn't be null");
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (!codeQualityConfiguration.isEnabled()) {
            getLog().info("Skipping code-quality execution");

            return;
        }

        try {
            executeSteps();

            // Hack to detect execution of the last module
            if (ProjectUtils.isLastModule(mavenSession)) {
                violationReporting();
            }
        } catch (final Exception e) {
            throw new MojoExecutionException(String.format("Failed during execution. Cause: %s", e.getMessage()), e);
        }
    }

    private ProjectStepResults executeSteps() {
        final ProjectStepResults projectStepResults = ProjectStepResults.ofResults(
            mavenSession.getCurrentProject().getName(),
            executeStep(checkstyleStep, codeQualityConfiguration, codeQualityConfiguration.getCheckstyle()),
            executeStep(errorProneStep, codeQualityConfiguration, codeQualityConfiguration.getErrorProne()),
            executeStep(checkerFrameworkStep, codeQualityConfiguration, codeQualityConfiguration.getCheckerFramework())
        );

        stepResultsRepository.store(projectStepResults);

        return projectStepResults;
    }

    private <T extends Configuration> StepResult executeStep(final Step<T> step, final CodeQualityConfiguration codeQualityConfiguration,
                                                             final T configuration) {
        try {
            if (!step.isEnabled(configuration)) {
                getLog().info(String.format("Step %s analyzer is disabled. Skipping...", step.type()));

                return StepResult.create(step.type(), configuration.isPermissive(), Collections.emptyList());
            }

            final Step.PrerequisiteResult prerequisiteResult = step.hasPrerequisites(configuration);
            if (!prerequisiteResult.hasAllPrerequisites()) {
                getLog().info(String.format("Step %s is missing prerequisites to run. Cause: %s. Skipping...", step.type(), prerequisiteResult.cause()));

                return StepResult.create(step.type(), configuration.isPermissive(), Collections.emptyList());
            }

            cleanHandler.handle(step, getLog());

            getLog().info(String.format("Executing %s analyzer", step.type()));

            return step.execute(codeQualityConfiguration, configuration, getLog());
        } catch (final Exception e) {
            getLog().error(String.format("Error occurred during %s analyzer. Cause: %s ", step.type(), e.getMessage()));

            throw new StepExecutionException(String.format("Error during execution of %s analyzer step. Cause: %s", step.type(), e.getMessage()), e);
        }
    }

    // TODO (nw) option to select which violation reports to apply
    private void violationReporting() {
        final StepResults stepResults = diffCoverageApplier.apply(stepResultsRepository.getAll(), getLog());

        violationReporters.forEach(r -> r.report(getLog(), stepResults));

        final List<Violation> nonPermissiveViolations = stepResults.getNonPermissiveViolations(Severity.MINOR);

        if (!nonPermissiveViolations.isEmpty()) {
            throw new SeverityThresholdException("Severity threshold has been exceeded.");
        }
    }
}
