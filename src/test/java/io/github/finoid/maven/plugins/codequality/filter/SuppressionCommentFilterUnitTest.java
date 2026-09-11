package io.github.finoid.maven.plugins.codequality.filter;

import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import io.github.finoid.maven.plugins.codequality.report.Severity;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class SuppressionCommentFilterUnitTest extends UnitTest {
    private static final String RULE = "TransactionRules.NO_TRANSACTIONAL_METHOD_SHOULD_START_A_SECOND_TRANSACTION";
    private static final String OTHER_RULE = "NullAway";

    @Mock
    private Log log;

    @TempDir
    private Path temporaryDirectory;

    private SuppressionCommentFilter unit;

    @BeforeEach
    void beforeEach() {
        unit = new SuppressionCommentFilter();
    }

    @Test
    @DisplayName("Given a suppression on the line above, When filtered, Then the violation is dropped")
    void suppressesFromTheLineAbove() throws IOException {
        final Path source = source(
            "package demo;",
            "class A {",
            "    // suppress:" + RULE + " provisioning is already atomic",
            "    void a() {",
            "    }",
            "}");

        final Violations result = filter(violation(source, 4, RULE));

        Assertions.assertEquals(0, result.total());
    }

    @Test
    @DisplayName("Given a suppression on the reported line itself, When filtered, Then the violation is dropped")
    void suppressesFromTheLineItself() throws IOException {
        final Path source = source(
            "package demo;",
            "class A {",
            "    void a() { // suppress:" + RULE,
            "    }",
            "}");

        Assertions.assertEquals(0, filter(violation(source, 3, RULE)).total());
    }

    @Test
    @DisplayName("Given a suppression naming another rule, When filtered, Then the violation is kept")
    void keepsAViolationSuppressedUnderAnotherRule() throws IOException {
        final Path source = source(
            "package demo;",
            "class A {",
            "    // suppress:" + OTHER_RULE,
            "    void a() {",
            "    }",
            "}");

        Assertions.assertEquals(1, filter(violation(source, 4, RULE)).total());
    }

    @Test
    @DisplayName("Given a bare suppression without a rule, When filtered, Then the violation is kept")
    void keepsAViolationUnderABareSuppression() throws IOException {
        final Path source = source(
            "package demo;",
            "class A {",
            "    // suppress",
            "    void a() {",
            "    }",
            "}");

        Assertions.assertEquals(1, filter(violation(source, 4, RULE)).total());
    }

    @Test
    @DisplayName("Given one comment naming two rules, When filtered, Then both violations are dropped")
    void suppressesSeveralRulesFromOneComment() throws IOException {
        final Path source = source(
            "package demo;",
            "class A {",
            "    // suppress:" + RULE + " suppress:" + OTHER_RULE,
            "    void a() {",
            "    }",
            "}");

        final Violations result = filter(violation(source, 4, RULE), violation(source, 4, OTHER_RULE));

        Assertions.assertEquals(0, result.total());
    }

    @Test
    @DisplayName("Given a suppression two lines above, When filtered, Then the violation is kept")
    void doesNotReachBeyondTheLineAbove() throws IOException {
        final Path source = source(
            "package demo;",
            "    // suppress:" + RULE,
            "class A {",
            "    void a() {",
            "    }",
            "}");

        Assertions.assertEquals(1, filter(violation(source, 4, RULE)).total());
    }

    @Test
    @DisplayName("Given a non permissive violation, When suppressed, Then it no longer fails the build")
    void suppressesNonPermissiveViolationsToo() throws IOException {
        final Path source = source(
            "package demo;",
            "// suppress:" + RULE,
            "class A {",
            "}");

        final Violations result =
            unit.filter(new Violations(List.of(), List.of(violation(source, 3, RULE))), new ViolationFilter.Context(log));

        Assertions.assertTrue(result.getNonPermissiveViolations().isEmpty());
    }

    @Test
    @DisplayName("Given a violation whose source is missing, When filtered, Then it is kept rather than dropped")
    void keepsAViolationWithoutAReadableSource() {
        final Violation violation = violation(temporaryDirectory.resolve("Gone.java"), 3, RULE);

        Assertions.assertEquals(1, filter(violation).total());
    }

    private Violations filter(final Violation... violations) {
        return unit.filter(new Violations(List.of(violations), List.of()), new ViolationFilter.Context(log));
    }

    private Path source(final String... lines) throws IOException {
        return Files.write(temporaryDirectory.resolve("A.java"), List.of(lines));
    }

    private static Violation violation(final Path source, final int line, final String rule) {
        return Violation.builder()
            .tool("ArchUnit")
            .description("something")
            .fingerprint("fingerprint")
            .severity(Severity.MAJOR)
            .relativePath("src/main/java/demo/A.java")
            .fullPath(source.toString())
            .line(line)
            .columnNumber(0)
            .rule(rule)
            .build();
    }
}
