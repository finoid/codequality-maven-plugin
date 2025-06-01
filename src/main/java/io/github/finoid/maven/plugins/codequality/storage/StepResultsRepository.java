package io.github.finoid.maven.plugins.codequality.storage;

import org.apache.maven.execution.MavenSession;
import io.github.finoid.maven.plugins.codequality.step.ProjectStepResults;
import io.github.finoid.maven.plugins.codequality.step.StepResults;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class StepResultsRepository {
    private final SessionRepository sessionRepository;
    private final MavenSession session;

    @Inject
    public StepResultsRepository(final SessionRepository sessionRepository, final MavenSession session) {
        this.sessionRepository = sessionRepository;
        this.session = session;
    }

    /**
     * Stores the given {@link ProjectStepResults} for the current Maven project.
     *
     * @param projectStepResults the step results to store
     */
    public void store(final ProjectStepResults projectStepResults) {
        sessionRepository.put(projectStorageKey(session.getCurrentProject().getName()), projectStepResults);
    }

    /**
     * Retrieves step results for all projects in the current Maven session.
     *
     * @return an aggregated {@link StepResults} instance containing results from all projects
     */
    public StepResults getAll() {
        final List<ProjectStepResults> results = session.getAllProjects().stream()
            .map(it -> sessionRepository.get(projectStorageKey(it.getName())))
            .filter(ProjectStepResults.class::isInstance)
            .map(ProjectStepResults.class::cast)
            .toList();

        return StepResults.ofResults(results);
    }

    private static String projectStorageKey(final String projectName) {
        return "step_result_" + projectName;
    }
}
