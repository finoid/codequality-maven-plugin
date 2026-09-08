package io.github.finoid.maven.plugins.codequality.storage;

import io.github.finoid.maven.plugins.codequality.util.Precondition;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Determines which mojo execution is the one to aggregate and report the results of the whole reactor.
 * <p>
 * The results of every module are reported once, by the module which finishes last. Picking the last module of the
 * sorted project list only holds for a sequential build - a parallel build ({@code mvn -T}) schedules modules as soon
 * as their dependencies are done, so the last module of the build order is regularly not the last one to finish, and
 * the results of the modules still running would be missing from the report.
 * <p>
 * Each execution instead records that its module is done and then checks whether every module which is expected to run
 * the goal has done the same. Exactly one execution observes a complete set - the set is only ever added to, and an
 * addition is visible to the thread which observes the completion - and that execution claims the reporting.
 */
@Singleton
public class ReactorCompletionTracker {
    private static final String COMPLETED_PROJECTS_KEY = "code_quality_completed_projects";
    private static final String REPORTING_CLAIMED_KEY = "code_quality_reporting_claimed";

    private final SessionRepository sessionRepository;

    @Inject
    public ReactorCompletionTracker(final SessionRepository sessionRepository) {
        this.sessionRepository = Precondition.nonNull(sessionRepository, "SessionRepository shouldn't be null");
    }

    /**
     * Records the given project as done, and claims the reporting of the reactor if it was the last one to finish.
     *
     * @param session   the Maven session
     * @param project   the module which just finished its code quality analysis
     * @param pluginKey the {@code groupId:artifactId} of this plugin
     * @return {@code true} if the caller is the one to report the results of the reactor, {@code false} otherwise
     */
    public boolean markCompletedAndClaimReporting(final MavenSession session, final MavenProject project, final String pluginKey) {
        final Set<String> completedProjectIds = completedProjectIds();

        completedProjectIds.add(project.getId());

        if (!completedProjectIds.containsAll(expectedProjectIds(session, pluginKey))) {
            return false;
        }

        return reportingClaimed().compareAndSet(false, true);
    }

    /**
     * The modules which are expected to run the goal, being those declaring the plugin. Falls back to every module of
     * the reactor, covering the goal being invoked directly from the command line rather than through a lifecycle
     * binding, in which case no module has to declare the plugin at all.
     */
    private static Set<String> expectedProjectIds(final MavenSession session, final String pluginKey) {
        final Set<String> declaringProjectIds = session.getProjects().stream()
            .filter(it -> declaresPlugin(it, pluginKey))
            .map(MavenProject::getId)
            .collect(Collectors.toSet());

        if (declaringProjectIds.isEmpty()) {
            return session.getProjects().stream()
                .map(MavenProject::getId)
                .collect(Collectors.toSet());
        }

        return declaringProjectIds;
    }

    private static boolean declaresPlugin(final MavenProject project, final String pluginKey) {
        return project.getBuildPlugins().stream()
            .map(Plugin::getKey)
            .anyMatch(pluginKey::equals);
    }

    @SuppressWarnings("unchecked")
    private Set<String> completedProjectIds() {
        return (Set<String>) sessionRepository.computeIfAbsent(COMPLETED_PROJECTS_KEY, ConcurrentHashMap::newKeySet);
    }

    private AtomicBoolean reportingClaimed() {
        return (AtomicBoolean) sessionRepository.computeIfAbsent(REPORTING_CLAIMED_KEY, () -> new AtomicBoolean(false));
    }
}
