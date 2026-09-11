package io.github.finoid.maven.plugins.codequality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Runs the ArchUnit step against a reactor whose rules live in a library rather than in the analyzed module.
 * <p>
 * The point of the fixture is where the rules come from. The {@code rules} module produces an ordinary jar holding an
 * {@code ArchRule} constant and an {@code ArchRuleProvider}, and the {@code app} module does nothing but depend on
 * that jar in test scope: one rule is referenced by name from the plugin configuration, the other is found through
 * the service loader without {@code app} mentioning it at all. Both therefore have to be loaded from the dependency,
 * which is what separates this step from the analyzers whose checks are built into the plugin.
 * <p>
 * The remaining assertions cover what a unit test cannot reach: that the violations survive into the aggregated
 * GitLab report with a repository relative path, that a per rule severity override keyed by the reported rule name
 * applies, and that a non permissive run fails the build.
 */
class CodeQualityArchUnitIT {
    private static final String FIXTURE = "it/archunit-reactor";

    private static final String EXPLICIT_RULE = "ItRules.NO_IMPL_SUFFIX";
    private static final String PROVIDED_RULE = "PROVIDED_NO_PUBLIC_MUTABLE_STATICS";

    private static final String IMPL_PATH = "app/src/main/java/it/app/FooImpl.java";
    private static final String COUNTERS_PATH = "app/src/main/java/it/app/Counters.java";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static String pluginVersion;

    @BeforeAll
    static void beforeAll() {
        pluginVersion = System.getProperty("it.plugin.version");

        Assertions.assertNotNull(pluginVersion, "The it.plugin.version system property has to be provided by the failsafe configuration");
    }

    @Test
    @DisplayName("Rules referenced from, and provided by, a dependency are both evaluated and reported")
    void givenRulesInADependency_whenVerify_thenBothSourcesAreEvaluated() throws Exception {
        final Path basedir = copyFixture("archunit");

        final Verifier verifier = verifier(basedir);
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();

        final List<ReportedViolation> violations = aggregatedViolations(basedir);
        final Map<String, ReportedViolation> byPath = new LinkedHashMap<>();

        violations.forEach(violation -> byPath.put(violation.path(), violation));

        Assertions.assertEquals(2, violations.size(),
            () -> "Expected one violation per rule, the explicitly referenced one and the provided one, but got " + violations);

        Assertions.assertTrue(byPath.containsKey(IMPL_PATH),
            () -> "The explicitly referenced rule is expected to report " + IMPL_PATH + ", got " + byPath.keySet());
        Assertions.assertTrue(byPath.containsKey(COUNTERS_PATH),
            () -> "The provided rule is expected to report " + COUNTERS_PATH + ", got " + byPath.keySet());

        violations.forEach(violation -> Assertions.assertTrue(violation.description().startsWith("ArchUnit: "),
            () -> "Unexpected non ArchUnit violation: " + violation));

        violations.forEach(violation -> Assertions.assertTrue(violation.line() >= 1,
            () -> "A violation is expected to carry a line, never zero: " + violation));

        // The severity override is keyed by the reported rule name, which only the explicitly referenced rule has one for
        Assertions.assertEquals("blocker", byPath.get(IMPL_PATH).severity());
        Assertions.assertEquals("major", byPath.get(COUNTERS_PATH).severity());

        final String log = logOf(basedir);

        Assertions.assertTrue(log.contains("[ArchUnit - " + EXPLICIT_RULE + "]"),
            "The explicitly referenced rule is expected to be reported under its normalised name");
        Assertions.assertTrue(log.contains("[ArchUnit - " + PROVIDED_RULE + "]"),
            "The provided rule is expected to be reported under the name its provider gave it");
    }

    @Test
    @DisplayName("A non permissive run fails the build on the violations found in the dependency's rules")
    void givenNonPermissiveRun_whenVerify_thenBuildFails() throws Exception {
        final Path basedir = copyFixture("archunit-strict");

        final Verifier verifier = verifier(basedir);
        verifier.addCliOption("-Dcq.it.archunit.permissive=false");

        Assertions.assertThrows(VerificationException.class, () -> verifier.executeGoal("verify"),
            "The build is expected to fail once the violations are not permissive");

        verifier.resetStreams();

        verifier.verifyTextInLog("Severity threshold has been exceeded.");
    }

    private Verifier verifier(final Path basedir) throws VerificationException {
        final Verifier verifier = new Verifier(basedir.toString());

        verifier.setForkJvm(true);
        verifier.setAutoclean(false);
        verifier.addCliOption("-Dcq.plugin.version=" + pluginVersion);

        return verifier;
    }

    private List<ReportedViolation> aggregatedViolations(final Path basedir) throws IOException {
        final Path report = basedir.resolve("target/gitlab-violations.json");

        Assertions.assertTrue(Files.isRegularFile(report), () -> "Missing aggregated report: " + report);

        final JsonNode reported = OBJECT_MAPPER.readTree(Files.readString(report, StandardCharsets.UTF_8));

        final List<ReportedViolation> violations = new ArrayList<>();

        for (final JsonNode violation : reported) {
            violations.add(new ReportedViolation(
                violation.path("location").path("path").asText(),
                violation.path("location").path("lines").path("begin").asInt(),
                violation.path("severity").asText(),
                violation.path("description").asText()));
        }

        return violations;
    }

    private String logOf(final Path basedir) throws IOException {
        return Files.readString(basedir.resolve("log.txt"), StandardCharsets.UTF_8);
    }

    private Path copyFixture(final String name) throws IOException {
        final Path fixtureRoot = Paths.get(System.getProperty("basedir", ""), "target", "test-classes", FIXTURE)
            .toAbsolutePath();

        Assertions.assertTrue(Files.isDirectory(fixtureRoot), () -> "Missing integration test fixture: " + fixtureRoot);

        final Path target = Paths.get(System.getProperty("basedir", ""), "target", "it", name)
            .toAbsolutePath();

        deleteRecursively(target);

        Files.createDirectories(target);

        try (Stream<Path> sources = Files.walk(fixtureRoot)) {
            sources.forEach(source -> {
                final Path destination = target.resolve(fixtureRoot.relativize(source).toString());

                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.copy(source, destination);
                    }
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        return target;
    }

    private static void deleteRecursively(final Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                Files.delete(file);

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path directory, final IOException exception) throws IOException {
                Files.delete(directory);

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private record ReportedViolation(String path, int line, String severity, String description) {
    }
}
