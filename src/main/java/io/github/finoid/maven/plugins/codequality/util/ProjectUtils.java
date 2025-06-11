package io.github.finoid.maven.plugins.codequality.util;

import io.github.finoid.maven.plugins.codequality.log.LogLevel;
import lombok.experimental.UtilityClass;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for working with Maven projects, dependencies, and source directories.
 */
@UtilityClass
public final class ProjectUtils {
    /**
     * Resolves a list of files from the given source directories in the specified Maven project.
     *
     * @param sourceDirectories a list of source directory paths relative to the project's base directory.
     * @param project           the Maven project from which the base directory is determined.
     * @return a list of resolved {@link File} objects representing the source directories.
     */
    public static List<File> filesOfSourcesDirectories(final List<String> sourceDirectories, final MavenProject project) {
        return sourceDirectories.stream()
            .map(sourceDir -> FileUtils.resolveFile(project.getBasedir(), sourceDir))
            .toList();
    }

    /**
     * Checks if Lombok is present on the classpath of the given Maven project.
     *
     * @param project the Maven project whose dependencies are checked.
     * @return {@code true} if Lombok is found in the classpath, {@code false} otherwise.
     */
    public static boolean isLombokPresentOnClassPath(final MavenProject project) {
        return isPresentOnClassPath(project, "org.projectlombok", "lombok");
    }

    /**
     * Checks if a specific dependency is present on the classpath.
     *
     * @param project    the Maven project whose dependencies are checked.
     * @param groupId    the group ID of the dependency.
     * @param artifactId the artifact ID of the dependency.
     * @return {@code true} if the dependency is found on the classpath, {@code false} otherwise.
     */
    public static boolean isPresentOnClassPath(final MavenProject project, final String groupId, final String artifactId) {
        return project.getArtifacts().stream()
            .anyMatch(it -> groupId.equals(it.getGroupId()) && artifactId.equals(it.getArtifactId()));
    }

    /**
     * Retrieves the version of an optional dependency from the project's classpath.
     *
     * @param project    the Maven project whose dependencies are checked.
     * @param groupId    the group ID of the dependency.
     * @param artifactId the artifact ID of the dependency.
     * @return an {@link Optional} containing the dependency version if found, or an empty {@link Optional} if not present.
     */
    public static Optional<String> optionalArtifactVersion(final MavenProject project, final String groupId, final String artifactId) {
        return project.getArtifacts().stream()
            .filter(artifact -> groupId.equals(artifact.getGroupId()) && artifactId.equals(artifact.getArtifactId()))
            .map(Artifact::getVersion)
            .findFirst();
    }

    /**
     * Determines whether the current module is the last module in the Maven build sequence.
     *
     * @param mavenSession The current Maven session, which contains the project dependency graph.
     * @return {@code true} if the current module is the last in the build order, {@code false} otherwise.
     */
    public static boolean isLastModule(final MavenSession mavenSession) {
        return mavenSession.getProjectDependencyGraph()
            .getSortedProjects()
            .get(mavenSession.getProjectDependencyGraph().getSortedProjects().size() - 1)
            .getArtifactId()
            .equalsIgnoreCase(mavenSession.getCurrentProject().getArtifactId());
    }

    /**
     * Resolves the configured step log level from the Maven plugin confiﬁguration or falls back
     * to a provided default if the configuration is missing or incomplete.
     *
     * @param mavenSession the Maven session providing access to the current project
     * @param fallback     the fallback log level if the configuration is absent or invalid
     * @return the resolved step log level, or the fallback
     * @throws IllegalArgumentException if the step log level is invalid
     */
    public static LogLevel stepLogLevelOrFallback(final MavenSession mavenSession,
                                                  final LogLevel fallback) {
        final Plugin plugin = mavenSession.getCurrentProject()
            .getPlugin("io.github.finoid:codequality-maven-plugin");

        if (plugin == null || !(plugin.getConfiguration() instanceof Xpp3Dom config)) {
            return fallback;
        }

        final Xpp3Dom stepLogLevel = Optional.ofNullable(config.getChild("codeQuality"))
            .map(cfg -> cfg.getChild("stepLogLevel"))
            .orElse(null);

        if (stepLogLevel == null || stepLogLevel.getValue() == null) {
            return fallback;
        }

        return LogLevel.ofStringOrThrow(stepLogLevel.getValue().trim());
    }

    @Nullable
    public static String getProjectBuildDirectory(final MavenSession mavenSession) {
        MavenProject project = mavenSession.getCurrentProject();

        while (true) {
            MavenProject parent = project.getParent();

            if (parent == null || parent.getBasedir() == null) {
                return project.getBuild()
                    .getDirectory();
            }

            project = parent;
        }
    }
}
