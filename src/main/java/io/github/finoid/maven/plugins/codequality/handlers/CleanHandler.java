package io.github.finoid.maven.plugins.codequality.handlers;

import io.github.finoid.maven.plugins.codequality.configuration.CodeQualityConfiguration;
import io.github.finoid.maven.plugins.codequality.exceptions.CodeQualityException;
import io.github.finoid.maven.plugins.codequality.step.CleanContext;
import io.github.finoid.maven.plugins.codequality.step.Step;
import io.github.finoid.maven.plugins.codequality.util.MojoUtils.PluginUtils;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Objects;

import static org.twdata.maven.mojoexecutor.MojoExecutor.attribute;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;

@Singleton
public class CleanHandler {
    private final MavenSession session;
    private final MavenProject project;
    private final BuildPluginManager pluginManager;
    private final CodeQualityConfiguration codeQualityConfiguration;

    @Inject
    public CleanHandler(
        final MavenSession session,
        final MavenProject project,
        final BuildPluginManager pluginManager,
        final CodeQualityConfiguration codeQualityConfiguration
    ) {
        this.session = Precondition.nonNull(session, "MavenSession shouldn't be null");
        this.project = Precondition.nonNull(project, "MavenProject shouldn't be null");
        this.pluginManager = Precondition.nonNull(pluginManager, "BuildPluginManager shouldn't be null");
        this.codeQualityConfiguration = Precondition.nonNull(codeQualityConfiguration, "CodeQualityConfiguration shouldn't be null");
    }

    /**
     * Cleans the target directory for the provided {@link Step}.
     *
     * @param step the step
     * @param log  the mojo logger
     * @throws CodeQualityException if an error occurred
     * @throws NullPointerException if the step is null
     */
    public void handle(final Step<?> step, final Log log) {
        Objects.requireNonNull(step);

        try {
            switch (step.getCleanContext().getType()) {
                case ALL -> executeClean(step, log);
                case DIRECTORY -> executeCleanDirectory(step, log);
                default -> log.info("Skip cleaning for " + step.type());
            }
        } catch (final MojoExecutionException | IllegalStateException e) {
            throw new CodeQualityException("Error during cleaning. Cause: " + e.getMessage(), e);
        }
    }

    private void executeClean(final Step<?> step, final Log log) throws MojoExecutionException {
        log.info("Cleaning up for " + step.type());

        final PluginDescriptor descriptor =
            PluginUtils.pluginDescriptor("org.apache.maven.plugins", "maven-clean-plugin", codeQualityConfiguration.getVersions().getMavenClean());

        executeMojo(
            PluginUtils.pluginOfDescriptor(descriptor),
            goal("clean"),
            configuration(
                element(name("failOnError"), "true"),
                element(name("followSymLinks"), "false")
            ),
            executionEnvironment(project, session, pluginManager)
        );
    }

    private void executeCleanDirectory(final Step<?> step, final Log log) throws MojoExecutionException {
        log.info("Cleaning up directory for " + step.type());

        final PluginDescriptor descriptor =
            PluginUtils.pluginDescriptor("org.apache.maven.plugins", "maven-antrun-plugin", codeQualityConfiguration.getVersions().getMavenAntRun());

        final CleanContext cleanContext = step.getCleanContext();

        executeMojo(
            PluginUtils.pluginOfDescriptor(descriptor),
            goal("run"),
            configuration(
                element(name("target"),
                    element(name("delete"),
                        attribute(name("failOnError"), "false"),
                        element(name("fileset"),
                            attribute(name("dir"), cleanContext.optionalFileDirectory()
                                .orElseThrow(() -> new IllegalStateException("File directory shouldn't be null"))),
                            element(name("include"),
                                attribute(name("name"), cleanContext.optionalFileSelector()
                                    .orElseThrow(() -> new IllegalStateException("File selector shouldn't be null")))
                            )
                        )
                    )
                )
            ),
            executionEnvironment(project, session, pluginManager)
        );
    }
}
