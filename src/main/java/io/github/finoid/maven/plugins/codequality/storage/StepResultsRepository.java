package io.github.finoid.maven.plugins.codequality.storage;

import io.github.finoid.maven.plugins.codequality.step.ProjectStepResults;
import io.github.finoid.maven.plugins.codequality.step.StepResults;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

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
     * Stores the given {@link ProjectStepResults} for the given Maven project.
     *
     * @param project            the project the results were produced for
     * @param projectStepResults the step results to store
     */
    public void store(final MavenProject project, final ProjectStepResults projectStepResults) {
        sessionRepository.put(projectStorageKey(project), projectStepResults);
    }

    /**
     * Retrieves step results for all projects in the current Maven session.
     *
     * @return an aggregated {@link StepResults} instance containing results from all projects
     */
    public StepResults getAll() {
        final List<ProjectStepResults> results = session.getAllProjects().stream()
            .map(it -> sessionRepository.get(projectStorageKey(it)))
            .filter(ProjectStepResults.class::isInstance)
            .map(ProjectStepResults.class::cast)
            .toList();

        return StepResults.ofResults(results);
    }

    /**
     * Keyed by the project id - {@code groupId:artifactId:packaging:version} - rather than by the project name, which
     * is neither required to be set nor to be unique within a reactor.
     */
    private static String projectStorageKey(final MavenProject project) {
        return "step_result_" + project.getId();
    }
}
