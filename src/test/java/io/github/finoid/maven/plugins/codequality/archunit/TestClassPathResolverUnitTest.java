package io.github.finoid.maven.plugins.codequality.archunit;

import io.github.finoid.maven.plugins.codequality.exceptions.CodeQualityException;
import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class TestClassPathResolverUnitTest extends UnitTest {
    @Mock
    private ProjectDependenciesResolver dependenciesResolver;
    @Mock
    private MavenSession session;
    @Mock
    private DependencyResolutionResult result;

    @TempDir
    private Path temporaryDirectory;

    private TestClassPathResolver unit;

    @BeforeEach
    void beforeEach() {
        unit = new TestClassPathResolver(dependenciesResolver, session);
    }

    @Test
    @DisplayName("Given a resolved dependency, When resolving, Then the output directories come first and the jar follows")
    void resolvesOutputDirectoriesAheadOfTheDependencies() throws Exception {
        final Path classes = Files.createDirectory(temporaryDirectory.resolve("classes"));
        final Path testClasses = Files.createDirectory(temporaryDirectory.resolve("test-classes"));
        final Path jar = Files.createFile(temporaryDirectory.resolve("rules.jar"));

        Mockito.when(result.getResolvedDependencies())
            .thenReturn(List.of(dependency(jar)));
        Mockito.when(dependenciesResolver.resolve(Mockito.any()))
            .thenReturn(result);

        final List<URL> classPath = unit.resolve(project(classes, testClasses));

        Assertions.assertEquals(
            List.of(classes.toUri().toURL(), testClasses.toUri().toURL(), jar.toUri().toURL()),
            classPath);
    }

    @Test
    @DisplayName("Given a dependency which was never resolved to a file, When resolving, Then it is left out")
    void skipsDependenciesWithoutAFile() throws Exception {
        final Path classes = Files.createDirectory(temporaryDirectory.resolve("classes"));

        Mockito.when(result.getResolvedDependencies())
            .thenReturn(List.of(new Dependency(new DefaultArtifact("g:a:1.0"), JavaScopes.TEST)));
        Mockito.when(dependenciesResolver.resolve(Mockito.any()))
            .thenReturn(result);

        final List<URL> classPath = unit.resolve(project(classes, temporaryDirectory.resolve("absent")));

        Assertions.assertEquals(List.of(classes.toUri().toURL()), classPath);
    }

    @Test
    @DisplayName("When resolving, Then the dependencies are filtered down to the test classpath")
    void filtersDownToTheTestClassPath() throws Exception {
        Mockito.when(result.getResolvedDependencies())
            .thenReturn(List.of());
        Mockito.when(dependenciesResolver.resolve(Mockito.any()))
            .thenReturn(result);

        unit.resolve(project(temporaryDirectory.resolve("absent"), temporaryDirectory.resolve("absent-too")));

        final ArgumentCaptor<DependencyResolutionRequest> request = ArgumentCaptor.forClass(DependencyResolutionRequest.class);
        Mockito.verify(dependenciesResolver)
            .resolve(request.capture());

        Assertions.assertNotNull(request.getValue().getResolutionFilter(),
            "The request is expected to carry a filter, so that only the test classpath is resolved");
    }

    @Test
    @DisplayName("Given unresolvable dependencies, When resolving, Then the module is named in the failure")
    void namesTheModuleWhenResolutionFails() throws Exception {
        // Built before the stubbing: its constructor calls into the result, which Mockito would otherwise read as
        // an unfinished stubbing of that mock
        final DependencyResolutionException failure = new DependencyResolutionException(result, "boom", new IOException("boom"));

        Mockito.when(dependenciesResolver.resolve(Mockito.any()))
            .thenThrow(failure);

        final MavenProject project = project(temporaryDirectory.resolve("absent"), temporaryDirectory.resolve("absent-too"));
        project.setArtifactId("the-module");

        final CodeQualityException exception = Assertions.assertThrows(CodeQualityException.class, () -> unit.resolve(project));

        Assertions.assertTrue(exception.getMessage().contains("the-module"), exception.getMessage());
    }

    private static Dependency dependency(final Path file) {
        return new Dependency(new DefaultArtifact("g:a:1.0").setFile(file.toFile()), JavaScopes.TEST);
    }

    private static MavenProject project(final Path classes, final Path testClasses) {
        final MavenProject project = new MavenProject();

        final Build build = new Build();
        build.setOutputDirectory(classes.toString());
        build.setTestOutputDirectory(testClasses.toString());

        project.setBuild(build);

        return project;
    }
}
