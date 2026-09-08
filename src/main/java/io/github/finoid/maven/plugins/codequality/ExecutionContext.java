package io.github.finoid.maven.plugins.codequality;

import io.github.finoid.maven.plugins.codequality.util.Precondition;
import lombok.Value;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

/**
 * The per mojo execution context, holding the state which differs between the modules of a reactor.
 * <p>
 * Components of the plugin are singletons, shared by every module of the reactor and - during a parallel build
 * ({@code mvn -T}) - by every builder thread. They must therefore never hold on to the {@link MavenProject}
 * currently being built. Neither an injected {@link MavenProject} (resolved once, when the singleton is created) nor
 * {@link org.apache.maven.execution.MavenSession#getCurrentProject()} of an injected session (the session which is
 * injected is the root session, whose current project is never advanced by the parallel builder) can be trusted.
 * <p>
 * The context is created by {@link CodeQuality} from its own mojo parameters, which Maven resolves per execution,
 * and is passed explicitly to every collaborator instead.
 */
@Value
public class ExecutionContext {
    /**
     * The module currently being analyzed.
     */
    MavenProject project;

    /**
     * The logger of the currently executing mojo.
     */
    Log log;

    public static ExecutionContext of(final MavenProject project, final Log log) {
        return new ExecutionContext(
            Precondition.nonNull(project, "MavenProject shouldn't be null"),
            Precondition.nonNull(log, "Log shouldn't be null")
        );
    }
}