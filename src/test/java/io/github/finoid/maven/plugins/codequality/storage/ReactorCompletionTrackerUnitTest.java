package io.github.finoid.maven.plugins.codequality.storage;

import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultSessionData;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

class ReactorCompletionTrackerUnitTest extends UnitTest {
    private static final String PLUGIN_KEY = "io.github.finoid:codequality-maven-plugin";

    @Test
    void givenModulesLeftToFinish_whenMarkCompleted_thenReportingIsNotClaimed() {
        var projects = declaringProjects("module-a", "module-b", "module-c");
        var session = sessionOf(projects);
        var unit = tracker(session);

        Assertions.assertFalse(unit.markCompletedAndClaimReporting(session, projects.get(0), PLUGIN_KEY));
        Assertions.assertFalse(unit.markCompletedAndClaimReporting(session, projects.get(1), PLUGIN_KEY));
    }

    @Test
    void givenLastModuleToFinish_whenMarkCompleted_thenReportingIsClaimed() {
        var projects = declaringProjects("module-a", "module-b");
        var session = sessionOf(projects);
        var unit = tracker(session);

        unit.markCompletedAndClaimReporting(session, projects.get(0), PLUGIN_KEY);

        Assertions.assertTrue(unit.markCompletedAndClaimReporting(session, projects.get(1), PLUGIN_KEY));
    }

    @Test
    void givenReportingAlreadyClaimed_whenMarkCompletedAgain_thenReportingIsNotClaimedTwice() {
        var projects = declaringProjects("module-a");
        var session = sessionOf(projects);
        var unit = tracker(session);

        Assertions.assertTrue(unit.markCompletedAndClaimReporting(session, projects.get(0), PLUGIN_KEY));
        Assertions.assertFalse(unit.markCompletedAndClaimReporting(session, projects.get(0), PLUGIN_KEY),
            "A module which runs the goal twice is expected to claim the reporting only once");
    }

    @Test
    void givenNoModuleDeclaresThePlugin_whenMarkCompleted_thenEveryModuleIsExpectedToFinish() {
        // Covers the goal being invoked straight from the command line, where no module has to declare the plugin
        var projects = List.of(project("module-a", false), project("module-b", false));
        var session = sessionOf(projects);
        var unit = tracker(session);

        Assertions.assertFalse(unit.markCompletedAndClaimReporting(session, projects.get(0), PLUGIN_KEY));
        Assertions.assertTrue(unit.markCompletedAndClaimReporting(session, projects.get(1), PLUGIN_KEY));
    }

    @Test
    void givenModulesNotDeclaringThePlugin_whenMarkCompleted_thenTheyAreNotWaitedFor() {
        var declaring = declaringProjects("module-a", "module-b");
        var projects = new ArrayList<>(declaring);
        projects.add(project("module-without-the-plugin", false));

        var session = sessionOf(projects);
        var unit = tracker(session);

        unit.markCompletedAndClaimReporting(session, declaring.get(0), PLUGIN_KEY);

        Assertions.assertTrue(unit.markCompletedAndClaimReporting(session, declaring.get(1), PLUGIN_KEY));
    }

    @Test
    void givenModulesFinishingConcurrently_whenMarkCompleted_thenExactlyOneClaimsTheReporting() throws Exception {
        final int modules = 8;

        // Repeated, a race is not guaranteed to show itself in a single run
        for (int attempt = 0; attempt < 50; attempt++) {
            var projects = declaringProjects(IntStream.range(0, modules)
                .mapToObj(it -> "module-" + it)
                .toArray(String[]::new));
            var session = sessionOf(projects);
            var unit = tracker(session);

            var barrier = new CyclicBarrier(modules);
            var executor = Executors.newFixedThreadPool(modules);

            try {
                var claims = executor.invokeAll(projects.stream()
                    .map(project -> (Callable<Boolean>) () -> {
                        barrier.await(10, TimeUnit.SECONDS);

                        return unit.markCompletedAndClaimReporting(session, project, PLUGIN_KEY);
                    })
                    .toList());

                Assertions.assertEquals(1, countClaims(claims),
                    "Exactly one of the concurrently finishing modules is expected to claim the reporting");
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static long countClaims(final List<Future<Boolean>> claims) throws Exception {
        long claimed = 0;

        for (final Future<Boolean> claim : claims) {
            if (Boolean.TRUE.equals(claim.get(10, TimeUnit.SECONDS))) {
                claimed++;
            }
        }

        return claimed;
    }

    private static ReactorCompletionTracker tracker(final MavenSession session) {
        return new ReactorCompletionTracker(new SessionRepository(session));
    }

    /**
     * A session backed by a real {@link DefaultSessionData}, so that the tracker exercises the concurrent data context
     * it relies on rather than a stub of it.
     */
    private static MavenSession sessionOf(final List<MavenProject> projects) {
        final MavenSession session = Mockito.mock(MavenSession.class);

        Mockito.lenient().when(session.getProjects())
            .thenReturn(projects);
        final RepositorySystemSession repositorySession = Mockito.mock(RepositorySystemSession.class);

        Mockito.lenient().when(repositorySession.getData())
            .thenReturn(new DefaultSessionData());
        Mockito.lenient().when(session.getRepositorySession())
            .thenReturn(repositorySession);

        return session;
    }

    private static List<MavenProject> declaringProjects(final String... artifactIds) {
        return Arrays.stream(artifactIds)
            .map(it -> project(it, true))
            .toList();
    }

    private static MavenProject project(final String artifactId, final boolean declaresThePlugin) {
        final MavenProject project = new MavenProject();

        project.setGroupId("io.github.finoid.it");
        project.setArtifactId(artifactId);
        project.setVersion("1.0");
        project.setPackaging("jar");

        final Build build = new Build();

        if (declaresThePlugin) {
            final Plugin plugin = new Plugin();
            plugin.setGroupId("io.github.finoid");
            plugin.setArtifactId("codequality-maven-plugin");

            build.addPlugin(plugin);
        }

        project.getModel().setBuild(build);

        return project;
    }
}
