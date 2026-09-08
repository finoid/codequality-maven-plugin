package io.github.finoid.maven.plugins.codequality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.VerificationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs the plugin against small reactors of three modules, each with a violation of its own, both sequentially and in
 * parallel.
 * <p>
 * The reactor is what the per module state of the plugin used to be unable to cope with. Every collaborator of the mojo
 * is a singleton, shared by all modules, so anything derived from an injected {@code MavenProject} - the source
 * directories to analyze, the Checkstyle cache file, the directory the output of the forked compiler is written to -
 * used to be pinned to whichever module happened to create the singleton first. In a parallel build the aggregation of
 * the results was wrong on top of that, being triggered by the last module of the build order rather than by the last
 * module to actually finish.
 * <p>
 * The assertions below therefore check that every module contributes its own violations, and that the aggregated report
 * is written exactly once, whichever way the reactor is built.
 * <p>
 * Both fixtures carry a {@code .mvn} directory of their own. Without it Maven walks up and picks up the one of this
 * repository, whose {@code maven.config} points at a settings file with a path relative to the reactor being built.
 */
class CodeQualityReactorIT {
    private static final String CHECKSTYLE_FIXTURE = "it/multi-module-reactor";
    private static final String ERROR_PRONE_FIXTURE = "it/error-prone-reactor";
    private static final String CHECKER_FRAMEWORK_FIXTURE = "it/checker-framework-reactor";

    private static final Map<String, String> EXPECTED_CHECKSTYLE_VIOLATIONS_BY_PATH = Map.of(
        "module-a/src/main/java/it/alpha/Alpha.java", "Unused import - java.util.List.",
        "module-b/src/main/java/it/beta/Beta.java", "Empty statement.",
        "module-c/src/main/java/it/gamma/Gamma.java", "Literal Strings should be compared using equals(), not '=='."
    );

    private static final Set<String> EXPECTED_COMPILER_STEP_PATHS = Set.of(
        "module-a/src/main/java/it/alpha/Alpha.java",
        "module-b/src/main/java/it/beta/Beta.java",
        "module-c/src/main/java/it/gamma/Gamma.java"
    );

    private static final List<String> MODULES = List.of("module-a", "module-b", "module-c");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static String pluginVersion;

    @BeforeAll
    static void beforeAll() {
        pluginVersion = System.getProperty("it.plugin.version");

        Assertions.assertNotNull(pluginVersion, "The it.plugin.version system property has to be provided by the failsafe configuration");
    }

    @Test
    @DisplayName("A sequentially built reactor reports the Checkstyle violations of every module exactly once")
    void givenReactor_whenVerifySequentially_thenViolationsOfEveryModuleAreReportedOnce() throws Exception {
        final Path basedir = copyFixture(CHECKSTYLE_FIXTURE, "sequential");

        final Verifier verifier = verifier(basedir);
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();

        assertCheckstyleReport(basedir);
        assertReportedOnce(basedir);
    }

    @Test
    @DisplayName("A parallel built reactor reports the same Checkstyle violations, without Maven flagging the goal as unsafe")
    void givenReactor_whenVerifyInParallel_thenViolationsOfEveryModuleAreReportedOnce() throws Exception {
        final Path basedir = copyFixture(CHECKSTYLE_FIXTURE, "parallel");

        final Verifier verifier = verifier(basedir);
        verifier.addCliOption("-T");
        verifier.addCliOption("4");
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();

        assertCheckstyleReport(basedir);
        assertReportedOnce(basedir);

        Assertions.assertFalse(
            logOf(basedir).contains("not marked as thread-safe"),
            "Maven flagged the code-quality goal as not thread-safe, the mojo is expected to declare threadSafe = true");
    }

    @Test
    @DisplayName("A non permissive parallel build fails on the aggregated violations of every module")
    void givenNonPermissiveReactor_whenVerifyInParallel_thenBuildFailsOnAggregatedViolations() throws Exception {
        final Path basedir = copyFixture(CHECKSTYLE_FIXTURE, "parallel-strict");

        final Verifier verifier = verifier(basedir);
        verifier.addCliOption("-T");
        verifier.addCliOption("4");
        verifier.addCliOption("-Dcq.it.checkstyle.permissive=false");

        Assertions.assertThrows(VerificationException.class, () -> verifier.executeGoal("verify"),
            "The build is expected to fail once the violations are not permissive");

        verifier.resetStreams();

        verifier.verifyTextInLog("Severity threshold has been exceeded.");

        // The violations of every module have to be part of the report the build fails on, not only the ones of the
        // module which happens to finish last
        assertCheckstyleReport(basedir);
    }

    /**
     * Error Prone is the step which forks the compiler per module and reads its findings back from a log file, written
     * by {@link MojoLogDecoratorExecutionListener} into the build directory of the module being compiled. Both the
     * directory that file is written to and the one it is read back from used to come from a pinned project, so the
     * modules of a parallel build wrote over, and read, each other's output.
     */
    @Test
    @DisplayName("A parallel built reactor reports the Error Prone violations of every module")
    void givenErrorProneReactor_whenVerifyInParallel_thenViolationsOfEveryModuleAreReported() throws Exception {
        final Path basedir = copyFixture(ERROR_PRONE_FIXTURE, "error-prone-parallel");

        final Verifier verifier = verifier(basedir);
        verifier.addCliOption("-T");
        verifier.addCliOption("4");
        verifier.executeGoal("verify");
        verifier.resetStreams();

        final List<ReportedViolation> violations = aggregatedViolations(basedir);

        Assertions.assertEquals(EXPECTED_COMPILER_STEP_PATHS, pathsOf(violations),
            () -> "Every module is expected to contribute its own Error Prone violations, but got " + violations);

        // Every module has to have been analyzed by Error Prone, rather than a single module three times over
        violations.forEach(violation -> Assertions.assertTrue(violation.description().startsWith("ErrorProne: "),
            () -> "Unexpected non Error Prone violation: " + violation));

        assertPerModuleOutput(basedir, "errorprone");
        assertReportedOnce(basedir);
    }

    /**
     * The Checker Framework step forks the compiler exactly like Error Prone does, and reads its findings back from the
     * same kind of per module log file, so it is prone to the same cross module mix ups.
     */
    @Test
    @DisplayName("A parallel built reactor reports the Checker Framework violations of every module")
    void givenCheckerFrameworkReactor_whenVerifyInParallel_thenViolationsOfEveryModuleAreReported() throws Exception {
        // Not under this project's own target directory, see copyFixtureOutsideBuildDirectory
        final Path basedir = copyFixtureOutsideBuildDirectory(CHECKER_FRAMEWORK_FIXTURE, "checker-framework-parallel");

        final Verifier verifier = verifier(basedir);
        verifier.addCliOption("-T");
        verifier.addCliOption("4");
        // Keeps checker-qual, which the step requires on the class path, in step with the checker the plugin runs
        verifier.addCliOption("-Dcq.it.checker.version=" + checkerFrameworkVersion());
        verifier.executeGoal("verify");
        verifier.resetStreams();

        final List<ReportedViolation> violations = aggregatedViolations(basedir);

        Assertions.assertEquals(EXPECTED_COMPILER_STEP_PATHS, pathsOf(violations),
            () -> "Every module is expected to contribute its own Checker Framework violations, but got " + violations);

        violations.forEach(violation -> Assertions.assertTrue(violation.description().startsWith("CheckerFramework: "),
            () -> "Unexpected non Checker Framework violation: " + violation));

        assertPerModuleOutput(basedir, "checkerframework");
        assertReportedOnce(basedir);

        // Only on success, a failed run is worth keeping around to look at
        deleteRecursively(basedir.getParent());
    }

    /**
     * Asserts that the output of the forked compiler of every module was written next to that module, rather than next
     * to whichever module a pinned project happened to be.
     */
    private void assertPerModuleOutput(final Path basedir, final String prefix) {
        for (final String module : MODULES) {
            final Path log = basedir.resolve(module + "/target/" + prefix + "-" + module + ".txt");

            Assertions.assertTrue(Files.isRegularFile(log), () -> "Missing analyzer output of " + module + ": " + log);
        }
    }

    /**
     * The Checker Framework version the plugin defaults to, read from the properties the build filters it into.
     */
    private static String checkerFrameworkVersion() throws IOException {
        final Properties properties = new Properties();

        try (InputStream stream = CodeQualityReactorIT.class.getResourceAsStream("/checkerframework-versions.properties")) {
            Assertions.assertNotNull(stream, "Missing checkerframework-versions.properties on the test class path");

            properties.load(stream);
        }

        final String version = properties.getProperty("checkerframework.version");

        Assertions.assertNotNull(version, "Missing checkerframework.version property");

        return version;
    }

    private Verifier verifier(final Path basedir) throws VerificationException {
        final Verifier verifier = new Verifier(basedir.toString());

        verifier.setForkJvm(true);
        verifier.setAutoclean(false);
        verifier.addCliOption("-Dcq.plugin.version=" + pluginVersion);

        return verifier;
    }

    /**
     * Asserts that the aggregated report of the reactor root holds exactly the Checkstyle violation of every module,
     * being one per module, and no duplicates of any of them.
     */
    private void assertCheckstyleReport(final Path basedir) throws IOException {
        final List<ReportedViolation> violations = aggregatedViolations(basedir);

        final Map<String, String> violationsByPath = new LinkedHashMap<>();

        violations.forEach(violation -> violationsByPath.put(violation.path(), violation.description()));

        Assertions.assertEquals(EXPECTED_CHECKSTYLE_VIOLATIONS_BY_PATH.keySet(), violationsByPath.keySet(),
            () -> "Unexpected set of violated files: " + violationsByPath);

        Assertions.assertEquals(EXPECTED_CHECKSTYLE_VIOLATIONS_BY_PATH.size(), violations.size(),
            () -> "Expected one violation per module, but got " + violations);

        EXPECTED_CHECKSTYLE_VIOLATIONS_BY_PATH.forEach((path, description) ->
            Assertions.assertEquals("Checkstyle: " + description, violationsByPath.get(path),
                () -> "Unexpected violation reported for " + path));
    }

    /**
     * Asserts that a single module aggregated and reported the results of the reactor.
     */
    private void assertReportedOnce(final Path basedir) throws IOException {
        final long reports = logOf(basedir).lines()
            .filter(it -> it.contains("permissive violations"))
            .count();

        // One line for the permissive violations, one for the non permissive ones
        Assertions.assertEquals(2, reports, "The results of the reactor are expected to be reported by exactly one module");
    }

    private List<ReportedViolation> aggregatedViolations(final Path basedir) throws IOException {
        final Path report = basedir.resolve("target/gitlab-violations.json");

        Assertions.assertTrue(Files.isRegularFile(report), () -> "Missing aggregated report: " + report);

        final JsonNode reported = OBJECT_MAPPER.readTree(Files.readString(report, StandardCharsets.UTF_8));

        final List<ReportedViolation> violations = new ArrayList<>();

        for (final JsonNode violation : reported) {
            violations.add(new ReportedViolation(
                violation.path("location").path("path").asText(),
                violation.path("description").asText()));
        }

        return violations;
    }

    private static Set<String> pathsOf(final List<ReportedViolation> violations) {
        return violations.stream()
            .map(ReportedViolation::path)
            .collect(Collectors.toSet());
    }

    private String logOf(final Path basedir) throws IOException {
        return Files.readString(basedir.resolve("log.txt"), StandardCharsets.UTF_8);
    }

    private Path copyFixture(final String fixture, final String name) throws IOException {
        return copyFixtureTo(fixture, Paths.get(System.getProperty("basedir", ""), "target", "it", name).toAbsolutePath());
    }

    /**
     * Copies a fixture to a working directory outside the build directory of this project.
     * <p>
     * The Checker Framework step passes {@code -AskipFiles=/target/} to keep generated sources out of the analysis, and
     * that pattern is matched against the whole path of every file. A fixture below this project's own {@code target}
     * directory would therefore be skipped in its entirety and the analyzer would report nothing at all.
     */
    private Path copyFixtureOutsideBuildDirectory(final String fixture, final String name) throws IOException {
        return copyFixtureTo(fixture, Files.createTempDirectory("codequality-it-").resolve(name));
    }

    private Path copyFixtureTo(final String fixture, final Path target) throws IOException {
        final Path fixtureRoot = Paths.get(System.getProperty("basedir", ""), "target", "test-classes", fixture)
            .toAbsolutePath();

        Assertions.assertTrue(Files.isDirectory(fixtureRoot), () -> "Missing integration test fixture: " + fixtureRoot);

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

    private record ReportedViolation(String path, String description) {
    }
}
