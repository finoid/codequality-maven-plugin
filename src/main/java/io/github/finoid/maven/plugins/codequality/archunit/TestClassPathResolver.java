package io.github.finoid.maven.plugins.codequality.archunit;

import io.github.finoid.maven.plugins.codequality.exceptions.CodeQualityException;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the test classpath of a module on demand.
 * <p>
 * The goal declares {@code ResolutionScope.COMPILE}, so {@link MavenProject#getTestClasspathElements()} is not
 * available: its artifacts carry no file. Widening the goal to {@code ResolutionScope.TEST} would resolve the test
 * dependencies of every module whether or not the ArchUnit step is enabled, and would make every project pay for a
 * step most do not use - including failing the goal on a test dependency which cannot be resolved. The test classpath
 * is therefore resolved here, and only when the step actually runs.
 * <p>
 * Only reactor wide state is read from the session, which makes the resolver safe to share between the modules of a
 * parallel build. The Aether session it hands to the resolver is the one of the whole reactor.
 */
@Singleton
public class TestClassPathResolver {
    private final ProjectDependenciesResolver dependenciesResolver;
    private final MavenSession session;

    @Inject
    public TestClassPathResolver(final ProjectDependenciesResolver dependenciesResolver, final MavenSession session) {
        this.dependenciesResolver = Precondition.nonNull(dependenciesResolver, "ProjectDependenciesResolver shouldn't be null");
        this.session = Precondition.nonNull(session, "MavenSession shouldn't be null");
    }

    /**
     * The test classpath of the module, being its own output directories followed by every dependency reachable in
     * test scope.
     *
     * @param project the module to resolve the test classpath of
     * @return the classpath entries, in classpath order
     * @throws CodeQualityException in case the dependencies cannot be resolved
     */
    public List<URL> resolve(final MavenProject project) {
        final List<URL> classPath = new ArrayList<>();

        addIfExists(classPath, project.getBuild().getOutputDirectory());
        addIfExists(classPath, project.getBuild().getTestOutputDirectory());

        for (final Dependency dependency : resolveDependencies(project)) {
            final File file = dependency.getArtifact()
                .getFile();

            if (file != null) {
                classPath.add(toUrl(file));
            }
        }

        return classPath;
    }

    private List<Dependency> resolveDependencies(final MavenProject project) {
        final DefaultDependencyResolutionRequest request =
            new DefaultDependencyResolutionRequest(project, session.getRepositorySession());

        request.setResolutionFilter(DependencyFilterUtils.classpathFilter(JavaScopes.TEST));

        try {
            final DependencyResolutionResult result = dependenciesResolver.resolve(request);

            return result.getResolvedDependencies();
        } catch (final DependencyResolutionException e) {
            throw new CodeQualityException(String.format(
                "Failed to resolve the test classpath of module [%s], which the ArchUnit step loads its rules from."
                    + " Cause: %s", project.getArtifactId(), e.getMessage()), e);
        }
    }

    private static void addIfExists(final List<URL> classPath, final String directory) {
        final File file = new File(directory);

        if (file.isDirectory()) {
            classPath.add(toUrl(file));
        }
    }

    private static URL toUrl(final File file) {
        try {
            return file.toURI()
                .toURL();
        } catch (final MalformedURLException e) {
            throw new CodeQualityException(String.format("Failed to build a class path URL of [%s]. Cause: %s", file, e.getMessage()), e);
        }
    }
}
