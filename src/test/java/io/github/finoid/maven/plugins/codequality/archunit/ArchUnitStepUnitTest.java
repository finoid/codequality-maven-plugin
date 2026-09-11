package io.github.finoid.maven.plugins.codequality.archunit;

import io.github.finoid.maven.plugins.codequality.ExecutionContext;
import io.github.finoid.maven.plugins.codequality.configuration.ArchUnitConfiguration;
import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import io.github.finoid.maven.plugins.codequality.step.ArchUnitStep;
import io.github.finoid.maven.plugins.codequality.step.Step;
import io.github.finoid.maven.plugins.codequality.configuration.CodeQualityConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

class ArchUnitStepUnitTest extends UnitTest {
    @Mock
    private ArchRuleResolver archRuleResolver;
    @Mock
    private ArchUnitAnalyzer archUnitAnalyzer;
    @Mock
    private TestClassPathResolver testClassPathResolver;
    @Mock
    private MavenSession mavenSession;
    @Mock
    private BuildPluginManager pluginManager;
    @Mock
    private Log log;

    @TempDir
    private Path temporaryDirectory;

    private ArchUnitStep unit;

    @BeforeEach
    void beforeEach() {
        unit = new ArchUnitStep(archRuleResolver, archUnitAnalyzer, testClassPathResolver,
            new CodeQualityConfiguration(), mavenSession, pluginManager);
    }

    @Test
    @DisplayName("Given a module which has not been compiled, Then the step reports a missing prerequisite")
    void reportsMissingPrerequisiteWithoutCompiledClasses() {
        final ArchUnitConfiguration configuration = new ArchUnitConfiguration();

        final Step.PrerequisiteResult result =
            unit.hasPrerequisites(configuration, context(temporaryDirectory.resolve("never-compiled")));

        Assertions.assertFalse(result.hasAllPrerequisites());
        Assertions.assertNotNull(result.cause());
        Assertions.assertTrue(result.cause().contains("compile"), result.cause());
    }

    @Test
    @DisplayName("Given compiled classes, Then the prerequisites are met")
    void acceptsACompiledModule() throws IOException {
        final Path classes = Files.createDirectory(temporaryDirectory.resolve("classes"));

        Assertions.assertTrue(unit.hasPrerequisites(new ArchUnitConfiguration(), context(classes)).hasAllPrerequisites());
    }

    @Test
    @DisplayName("Given an uncompiled module and compileIfMissing, Then the prerequisites are met")
    void acceptsAnUncompiledModuleWhenAllowedToCompileIt() {
        final ArchUnitConfiguration configuration = new ArchUnitConfiguration();
        configuration.setCompileIfMissing(true);

        Assertions.assertTrue(
            unit.hasPrerequisites(configuration, context(temporaryDirectory.resolve("never-compiled"))).hasAllPrerequisites());
    }

    @Test
    @DisplayName("Given an uncompiled module, Then the cause names the option which would compile it")
    void namesTheCompileOptionInTheCause() {
        final Step.PrerequisiteResult result =
            unit.hasPrerequisites(new ArchUnitConfiguration(), context(temporaryDirectory.resolve("never-compiled")));

        Assertions.assertTrue(result.cause().contains("compileIfMissing"), result.cause());
    }

    @Test
    @DisplayName("Given no rules and no service loader, Then the step reports a missing prerequisite")
    void reportsMissingPrerequisiteWithoutRules() throws IOException {
        final Path classes = Files.createDirectory(temporaryDirectory.resolve("classes"));

        final ArchUnitConfiguration configuration = new ArchUnitConfiguration();
        configuration.setServiceLoaderEnabled(false);
        configuration.setRules(Set.of());

        final Step.PrerequisiteResult result = unit.hasPrerequisites(configuration, context(classes));

        Assertions.assertFalse(result.hasAllPrerequisites());
    }

    private ExecutionContext context(final Path outputDirectory) {
        final MavenProject project = new MavenProject();

        final Build build = new Build();
        build.setOutputDirectory(outputDirectory.toString());
        project.setBuild(build);

        return ExecutionContext.of(project, log);
    }
}
