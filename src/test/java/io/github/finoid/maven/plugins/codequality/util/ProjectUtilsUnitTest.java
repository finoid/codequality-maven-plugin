package io.github.finoid.maven.plugins.codequality.util;

import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * Everything answered from the resolved artifact set filters by scope, so that the answer does not depend on the
 * resolution scope the goal happens to declare. A test or runtime scoped Lombok or checker-qual must never make an
 * analyzer believe it is available to the compilation of the main sources, which happens against the compile
 * classpath only: the Checker Framework step would pass its prerequisite and then fail the forked compile.
 */
class ProjectUtilsUnitTest extends UnitTest {
    private static final String GROUP_ID = "org.checkerframework";
    private static final String ARTIFACT_ID = "checker-qual";

    @Test
    @DisplayName("Given a compile scoped dependency, When looked up, Then it is found")
    void findsACompileScopedDependency() {
        final MavenProject project = projectWith(artifact(Artifact.SCOPE_COMPILE));

        Assertions.assertTrue(ProjectUtils.isPresentOnClassPath(project, GROUP_ID, ARTIFACT_ID));
        Assertions.assertEquals("1.0.0", ProjectUtils.optionalArtifactVersion(project, GROUP_ID, ARTIFACT_ID).orElse(null));
    }

    @Test
    @DisplayName("Given a provided scoped dependency, When looked up, Then it is found")
    void findsAProvidedScopedDependency() {
        final MavenProject project = projectWith(artifact(Artifact.SCOPE_PROVIDED));

        Assertions.assertTrue(ProjectUtils.isPresentOnClassPath(project, GROUP_ID, ARTIFACT_ID));
    }

    @Test
    @DisplayName("Given a test scoped dependency, When looked up, Then it is not found")
    void ignoresATestScopedDependency() {
        final MavenProject project = projectWith(artifact(Artifact.SCOPE_TEST));

        Assertions.assertFalse(ProjectUtils.isPresentOnClassPath(project, GROUP_ID, ARTIFACT_ID),
            "A test scoped dependency is not on the compile classpath the analyzers compile against");
        Assertions.assertTrue(ProjectUtils.optionalArtifactVersion(project, GROUP_ID, ARTIFACT_ID).isEmpty());
    }

    @Test
    @DisplayName("Given a runtime scoped dependency, When looked up, Then it is not found")
    void ignoresARuntimeScopedDependency() {
        final MavenProject project = projectWith(artifact(Artifact.SCOPE_RUNTIME));

        Assertions.assertFalse(ProjectUtils.isPresentOnClassPath(project, GROUP_ID, ARTIFACT_ID));
    }

    @Test
    @DisplayName("Given a dependency resolved without a scope, When looked up, Then it is treated as compile scoped")
    void treatsAMissingScopeAsCompile() {
        final MavenProject project = projectWith(artifact(null));

        Assertions.assertTrue(ProjectUtils.isPresentOnClassPath(project, GROUP_ID, ARTIFACT_ID));
    }

    private static MavenProject projectWith(final Artifact artifact) {
        final MavenProject project = new MavenProject();

        project.setArtifacts(Set.of(artifact));

        return project;
    }

    private static Artifact artifact(final String scope) {
        return new DefaultArtifact(GROUP_ID, ARTIFACT_ID, VersionRange.createFromVersion("1.0.0"), scope, "jar", null,
            new DefaultArtifactHandler("jar"));
    }
}
