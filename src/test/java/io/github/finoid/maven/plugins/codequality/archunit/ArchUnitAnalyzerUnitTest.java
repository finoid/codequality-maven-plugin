package io.github.finoid.maven.plugins.codequality.archunit;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.github.finoid.maven.plugins.codequality.ExecutionContext;
import io.github.finoid.maven.plugins.codequality.configuration.ArchUnitConfiguration;
import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import io.github.finoid.maven.plugins.codequality.report.Severity;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.step.ViolationConverter;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Runs the analyzer against the compiled classes of this very module, which is the only source of real bytecode
 * available to a unit test, and the only way to prove the source locations are mapped back onto real files.
 */
class ArchUnitAnalyzerUnitTest extends UnitTest {
    private static final Path WORKING_DIRECTORY = Paths.get("")
        .toAbsolutePath();

    @Mock
    private MavenSession session;
    @Mock
    private MavenExecutionRequest request;
    @Mock
    private Log log;

    private ArchUnitAnalyzer unit;
    private ExecutionContext context;

    @BeforeEach
    void beforeEach() {
        // Lenient: the repository root is only read when a violation is actually converted, and two of the tests
        // below deliberately produce none.
        Mockito.lenient()
            .when(session.getRequest())
            .thenReturn(request);
        Mockito.lenient()
            .when(request.getMultiModuleProjectDirectory())
            .thenReturn(WORKING_DIRECTORY.toFile());

        unit = new ArchUnitAnalyzer(new ViolationConverter(session));
        context = ExecutionContext.of(project(), log);
    }

    @Test
    @DisplayName("Given a rule no class satisfies, Then every violation carries the source file and line it belongs to")
    void reportsViolationsWithTheirSourceLocation() {
        final ArchRule rule = ArchRuleDefinition.classes()
            .that()
            .haveSimpleName("ArchUnitAnalyzer")
            .should()
            .haveSimpleName("SomethingElse")
            .allowEmptyShould(true);

        final List<Violation> violations = unit.analyze(List.of(NamedArchRule.of("NAMING", rule)), configuration(), context);

        Assertions.assertEquals(1, violations.size());

        final Violation violation = violations.getFirst();
        Assertions.assertEquals("ArchUnit", violation.getTool());
        Assertions.assertEquals("NAMING", violation.getRule());
        Assertions.assertEquals(
            "src/main/java/io/github/finoid/maven/plugins/codequality/archunit/ArchUnitAnalyzer.java",
            violation.getRelativePath());
        Assertions.assertTrue(violation.getLine() >= 1, "Line should never be reported as zero");
        Assertions.assertFalse(violation.getDescription().contains("\n"), "Description should be a single line");
    }

    @Test
    @DisplayName("Given a satisfied rule, Then nothing is reported")
    void reportsNothingForASatisfiedRule() {
        final ArchRule rule = ArchRuleDefinition.classes()
            .that()
            .haveSimpleName("ArchUnitAnalyzer")
            .should()
            .haveSimpleName("ArchUnitAnalyzer")
            .allowEmptyShould(true);

        Assertions.assertTrue(unit.analyze(List.of(NamedArchRule.of("NAMING", rule)), configuration(), context).isEmpty());
    }

    @Test
    @DisplayName("Given a per rule severity, Then it overrides the step wide default")
    void appliesThePerRuleSeverity() {
        final ArchUnitConfiguration configuration = configuration();
        configuration.setSeverity(Severity.MINOR);
        configuration.setRuleSeverities(Map.of("NAMING", Severity.BLOCKER));

        final ArchRule rule = ArchRuleDefinition.classes()
            .that()
            .haveSimpleName("ArchUnitAnalyzer")
            .should()
            .haveSimpleName("SomethingElse")
            .allowEmptyShould(true);

        final List<Violation> violations = unit.analyze(List.of(NamedArchRule.of("NAMING", rule)), configuration, context);

        Assertions.assertEquals(Severity.BLOCKER, violations.getFirst().getSeverity());
    }

    @Test
    @DisplayName("Given the same violation twice, Then the fingerprint is stable")
    void producesAStableFingerprint() {
        final ArchRule rule = ArchRuleDefinition.classes()
            .that()
            .haveSimpleName("ArchUnitAnalyzer")
            .should()
            .haveSimpleName("SomethingElse")
            .allowEmptyShould(true);

        final List<Violation> first = unit.analyze(List.of(NamedArchRule.of("NAMING", rule)), configuration(), context);
        final List<Violation> second = unit.analyze(List.of(NamedArchRule.of("NAMING", rule)), configuration(), context);

        Assertions.assertEquals(first.getFirst().getFingerprint(), second.getFirst().getFingerprint());
    }

    @Test
    @DisplayName("Given a module without compiled classes, Then nothing is analyzed rather than failing")
    void skipsAModuleWithoutCompiledClasses() {
        final MavenProject emptyProject = new MavenProject();
        final Build build = new Build();
        build.setOutputDirectory(WORKING_DIRECTORY.resolve("target/does-not-exist").toString());
        build.setTestOutputDirectory(WORKING_DIRECTORY.resolve("target/does-not-exist-either").toString());
        emptyProject.setBuild(build);
        emptyProject.setFile(WORKING_DIRECTORY.resolve("pom.xml").toFile());

        final ExecutionContext emptyContext = ExecutionContext.of(emptyProject, log);

        Assertions.assertTrue(unit.analyze(List.of(), configuration(), emptyContext).isEmpty());
    }

    private static ArchUnitConfiguration configuration() {
        return new ArchUnitConfiguration();
    }

    private static MavenProject project() {
        final MavenProject project = new MavenProject();

        final Build build = new Build();
        build.setOutputDirectory(WORKING_DIRECTORY.resolve("target/classes").toString());
        build.setTestOutputDirectory(WORKING_DIRECTORY.resolve("target/test-classes").toString());

        project.setBuild(build);
        project.setFile(WORKING_DIRECTORY.resolve("pom.xml").toFile());
        project.getCompileSourceRoots()
            .clear();
        project.addCompileSourceRoot(WORKING_DIRECTORY.resolve("src/main/java").toString());

        return project;
    }
}
