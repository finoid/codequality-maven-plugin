package io.github.finoid.maven.plugins.codequality.factories;

import com.puppycrawl.tools.checkstyle.AbstractAutomaticBean;
import com.puppycrawl.tools.checkstyle.DefaultLogger;
import io.github.finoid.maven.plugins.codequality.ExecutionContext;
import io.github.finoid.maven.plugins.codequality.configuration.CheckstyleConfiguration;
import io.github.finoid.maven.plugins.codequality.log.CheckstyleConsoleLogger;
import io.github.finoid.maven.plugins.codequality.log.LinkableAuditEventDefaultFormatter;
import io.github.finoid.maven.plugins.codequality.util.ProjectUtils;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.checkstyle.exec.CheckstyleExecutorRequest;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

import javax.inject.Singleton;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

import static com.puppycrawl.tools.checkstyle.AbstractAutomaticBean.OutputStreamOptions.NONE;

/**
 * Creates the {@link CheckstyleExecutorRequest} of a single module.
 * <p>
 * Stateless, and therefore safe to share between the builder threads of a parallel build. The module to create the
 * request for is taken from the {@link ExecutionContext} of the current mojo execution.
 */
@Singleton
public class CheckstyleExecutorRequestFactory {
    public CheckstyleExecutorRequest create(final CheckstyleConfiguration configuration,
                                            final CheckstyleConfiguration.ExecutionEnvironment executionEnvironment,
                                            final ExecutionContext context) {
        final MavenProject project = context.getProject();

        final CheckstyleExecutorRequest request = new CheckstyleExecutorRequest()
            .setIncludes(executionEnvironment.getIncludes())
            .setResourceIncludes(executionEnvironment.getResourceIncludes())
            .setSourceDirectories(sourceDirectories(executionEnvironment, project))
            .setConfigLocation(executionEnvironment.getConfigLocation())
            .setHeaderLocation(executionEnvironment.getHeaderLocation())
            .setCacheFile(project.getBuild().getDirectory() + "/" + executionEnvironment.getCacheFile())
            .setEncoding(executionEnvironment.getEncoding())
            .setOmitIgnoredModules(false) // TODO (nw) configurable via property?
            .setProject(project);

        executionEnvironment.optionalSuppressionLocation()
            .ifPresent(request::setSuppressionsLocation);

        if (configuration.isConsoleOutput()) {
            request
                .setConsoleOutput(true)
                .setConsoleListener(consoleListener(context.getLog()));
        }

        return request;
    }

    private List<File> sourceDirectories(final CheckstyleConfiguration.ExecutionEnvironment executionEnvironment, final MavenProject project) {
        if (executionEnvironment.getSourceDirectories() == null || executionEnvironment.getSourceDirectories().isEmpty()) {
            final List<String> compileSourceRoots = switch (executionEnvironment.getEnvironment()) {
                case MAIN -> filterBuildTarget(project.getCompileSourceRoots(), project);
                case TEST -> filterBuildTarget(project.getTestCompileSourceRoots(), project);
            };

            return ProjectUtils.filesOfSourcesDirectories(compileSourceRoots, project);
        }
        return executionEnvironment.getSourceDirectories();
    }

    private DefaultLogger consoleListener(final Log log) {
        // TODO (nw) change to a non buffer output stream?
        // TODO (nw) pass stepLogLevel to CheckstyleConsoleLogger, might be better with a boolean?

        return new CheckstyleConsoleLogger(
            OutputStream.nullOutputStream(),
            AbstractAutomaticBean.OutputStreamOptions.valueOf(NONE.name()),
            OutputStream.nullOutputStream(),
            AbstractAutomaticBean.OutputStreamOptions.NONE,
            new LinkableAuditEventDefaultFormatter(),
            log);
    }

    private List<String> filterBuildTarget(final List<String> compileSourceRoots, final MavenProject project) {
        final Path pathToProjectBuildTarget = FileUtils.resolveFile(project.getBasedir(), project.getBuild().getDirectory())
            .toPath();

        return compileSourceRoots.stream()
            .filter(sourceDir -> !sourceDirIsProjectBuildTarget(sourceDir, pathToProjectBuildTarget)).toList();
    }

    private static boolean sourceDirIsProjectBuildTarget(final String sourceDirectory, final Path pathToProjectBuildTarget) {
        return sourceDirectory.startsWith(pathToProjectBuildTarget.toString());
    }

}
