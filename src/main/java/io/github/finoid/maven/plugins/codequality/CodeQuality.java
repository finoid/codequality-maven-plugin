package io.github.finoid.maven.plugins.codequality;

import io.github.finoid.maven.plugins.codequality.configuration.CodeQualityConfiguration;
import io.github.finoid.maven.plugins.codequality.configuration.Configuration;
import io.github.finoid.maven.plugins.codequality.exceptions.SeverityThresholdException;
import io.github.finoid.maven.plugins.codequality.exceptions.StepExecutionException;
import io.github.finoid.maven.plugins.codequality.filter.Violations;
import io.github.finoid.maven.plugins.codequality.filter.ViolationsFilterService;
import io.github.finoid.maven.plugins.codequality.filter.ViolationsFilterService.Context;
import io.github.finoid.maven.plugins.codequality.handlers.CleanHandler;
import io.github.finoid.maven.plugins.codequality.report.Severity;
import io.github.finoid.maven.plugins.codequality.report.ViolationReporter;
import io.github.finoid.maven.plugins.codequality.step.CheckerFrameworkStep;
import io.github.finoid.maven.plugins.codequality.step.CheckstyleStep;
import io.github.finoid.maven.plugins.codequality.step.ErrorProneStep;
import io.github.finoid.maven.plugins.codequality.step.ProjectStepResults;
import io.github.finoid.maven.plugins.codequality.step.Step;
import io.github.finoid.maven.plugins.codequality.step.StepResult;
import io.github.finoid.maven.plugins.codequality.step.StepResults;
import io.github.finoid.maven.plugins.codequality.storage.ReactorCompletionTracker;
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
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

@Mojo(name = "code-quality", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class CodeQuality extends AbstractMojo {
    private final CheckstyleStep checkstyleStep;
    private final ErrorProneStep errorProneStep;
    private final CheckerFrameworkStep checkerFrameworkStep;
    private final CleanHandler cleanHandler;
    private final StepResultsRepository stepResultsRepository;
    private final ReactorCompletionTracker reactorCompletionTracker;
    private final List<ViolationReporter> violationReporters;
    private final ViolationsFilterService filterService;

    @Parameter(alias = "codeQuality")
    private CodeQualityConfiguration codeQualityConfiguration;

    /**
     * The session of the current mojo execution.
     * <p>
     * Resolved as a mojo parameter rather than injected: a parallel build ({@code mvn -T}) hands each module a copy of
     * the session, and only the copy resolved here knows which module is currently being built. The injected session
     * is the root session, whose current project is never advanced by the parallel builder.
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession mavenSession;

    /**
     * The module this execution analyzes.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Inject
    public CodeQuality(
        final CheckstyleStep checkstyleStep,
        final ErrorProneStep errorProneStep,
        final CheckerFrameworkStep checkerFrameworkStep,
        final CleanHandler cleanHandler,
        final MavenSession mavenSession,
        final MavenProject project,
        final StepResultsRepository stepResultsRepository,
        final ReactorCompletionTracker reactorCompletionTracker,
        final List<ViolationReporter> violationReporters,
        final ViolationsFilterService filterService,
        final CodeQualityConfiguration codeQualityConfiguration
    ) {
        this.checkstyleStep = Precondition.nonNull(checkstyleStep, "CheckstyleStep shouldn't be null");
        this.errorProneStep = Precondition.nonNull(errorProneStep, "ErrorProneStep shouldn't be null");
        this.checkerFrameworkStep = Precondition.nonNull(checkerFrameworkStep, "CheckerFrameworkStep shouldn't be null");
        this.cleanHandler = Precondition.nonNull(cleanHandler, "CleanHandler shouldn't be null");
        this.stepResultsRepository = Precondition.nonNull(stepResultsRepository, "StepResultsRepository shouldn't be null");
        this.reactorCompletionTracker = Precondition.nonNull(reactorCompletionTracker, "ReactorCompletionTracker shouldn't be null");
        this.violationReporters = Precondition.nonNull(violationReporters, "ViolationResultLogOutput shouldn't be null");
        this.filterService = Precondition.nonNull(filterService, "ViolationsFilterService shouldn't be null");
        this.codeQualityConfiguration = Precondition.nonNull(codeQualityConfiguration, "CodeQualityConfiguration shouldn't be null");
        // Seeded here to keep the fields non null, and overwritten with the per execution values by Maven once the
        // mojo parameters above have been resolved.
        this.mavenSession = Precondition.nonNull(mavenSession, "MavenSession shouldn't be null");
        this.project = Precondition.nonNull(project, "MavenProject shouldn't be null");
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (!codeQualityConfiguration.isEnabled()) {
            getLog().info("Skipping code-quality execution");

            return;
        }

        final ExecutionContext context = ExecutionContext.of(project, getLog());

        try {
            executeSteps(context);

            // The results of the whole reactor are reported once, by the module which finishes last
            if (reactorCompletionTracker.markCompletedAndClaimReporting(mavenSession, project, ProjectUtils.PLUGIN_KEY)) {
                final StepResults stepResults = stepResultsRepository.getAll();
                final Violations violations =
                    new Violations(stepResults.getViolations(Severity.MINOR, true), stepResults.getNonPermissiveViolations(Severity.MINOR));

                final Violations filteredViolations = filterService.filter(violations, new Context(getLog(), codeQualityConfiguration.getViolationFilters()));

                violationReporting(context, filteredViolations);
            }
        } catch (final Exception e) {
            throw new MojoExecutionException(String.format("Failed during execution. Cause: %s", e.getMessage()), e);
        }
    }

    private ProjectStepResults executeSteps(final ExecutionContext context) {
        final ProjectStepResults projectStepResults = ProjectStepResults.ofResults(
            context.getProject().getName(),
            executeStep(checkstyleStep, codeQualityConfiguration, codeQualityConfiguration.getCheckstyle(), context),
            executeStep(errorProneStep, codeQualityConfiguration, codeQualityConfiguration.getErrorProne(), context),
            executeStep(checkerFrameworkStep, codeQualityConfiguration, codeQualityConfiguration.getCheckerFramework(), context)
        );

        stepResultsRepository.store(context.getProject(), projectStepResults);

        return projectStepResults;
    }

    private <T extends Configuration> StepResult executeStep(final Step<T> step, final CodeQualityConfiguration codeQualityConfiguration,
                                                             final T configuration, final ExecutionContext context) {
        try {
            if (!step.isEnabled(configuration)) {
                context.getLog().info(String.format("Step %s analyzer is disabled. Skipping...", step.type()));

                return StepResult.create(step.type(), configuration.isPermissive(), Collections.emptyList());
            }

            final Step.PrerequisiteResult prerequisiteResult = step.hasPrerequisites(configuration, context);
            if (!prerequisiteResult.hasAllPrerequisites()) {
                context.getLog()
                    .info(String.format("Step %s is missing prerequisites to run. Cause: %s. Skipping...", step.type(), prerequisiteResult.cause()));

                return StepResult.create(step.type(), configuration.isPermissive(), Collections.emptyList());
            }

            cleanHandler.handle(step, context);

            context.getLog().info(String.format("Executing %s analyzer", step.type()));

            return step.execute(codeQualityConfiguration, configuration, context);
        } catch (final Exception e) {
            context.getLog().error(String.format("Error occurred during %s analyzer. Cause: %s ", step.type(), e.getMessage()));

            throw new StepExecutionException(String.format("Error during execution of %s analyzer step. Cause: %s", step.type(), e.getMessage()), e);
        }
    }

    private void violationReporting(final ExecutionContext context, final Violations violations) {
        violationReporters.stream()
            .filter(it -> codeQualityConfiguration.getViolationReporters().contains(it.name()))
            .forEach(r -> r.report(context, violations));

        if (!violations.getNonPermissiveViolations().isEmpty()) {
            throw new SeverityThresholdException("Severity threshold has been exceeded.");
        }
    }
}
