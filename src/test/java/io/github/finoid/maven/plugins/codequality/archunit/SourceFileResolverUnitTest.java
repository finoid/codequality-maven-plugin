package io.github.finoid.maven.plugins.codequality.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolved against the compiled classes and the sources of this very module, which is the only place a unit test can
 * get real bytecode and the source it came from to agree with each other.
 */
class SourceFileResolverUnitTest extends UnitTest {
    private static final Path WORKING_DIRECTORY = Paths.get("")
        .toAbsolutePath();

    private static final JavaClasses CLASSES = new ClassFileImporter().importClasses(SourceFileResolver.class, NamedArchRule.class);

    private SourceFileResolver unit;

    @BeforeEach
    void beforeEach() {
        unit = new SourceFileResolver(project(), false);
    }

    @Test
    @DisplayName("Given a method, When resolving its line, Then the declaration is reported rather than the first statement")
    void resolvesAMethodToItsDeclaration() {
        final JavaClass javaClass = CLASSES.get(SourceFileResolver.class);
        final JavaMethod method = javaClass.getMethod("moduleDirectory");

        final File file = unit.resolve(method.getSourceCodeLocation());
        final int line = unit.lineNumber(method, method.getSourceCodeLocation(), file);

        Assertions.assertTrue(sourceLineOf(file, line).contains("File moduleDirectory()"),
            () -> "Expected the declaration, got line " + line + ": " + sourceLineOf(file, line));
        Assertions.assertTrue(line < method.getSourceCodeLocation().getLineNumber(),
            "The declaration is expected to precede the first statement the bytecode reports");
    }

    @Test
    @DisplayName("Given an overloaded method, When resolving its line, Then the nearest preceding declaration is used")
    void resolvesTheRightOverload() {
        final JavaClass javaClass = CLASSES.get(SourceFileResolver.class);
        final JavaMethod method = javaClass.getMethod("resolve", com.tngtech.archunit.core.domain.SourceCodeLocation.class);

        final File file = unit.resolve(method.getSourceCodeLocation());
        final int line = unit.lineNumber(method, method.getSourceCodeLocation(), file);

        Assertions.assertTrue(sourceLineOf(file, line).contains("File resolve("),
            () -> "Expected the declaration, got line " + line + ": " + sourceLineOf(file, line));
    }

    @Test
    @DisplayName("Given a class, When resolving its line, Then the type declaration is reported rather than line one")
    void resolvesAClassToItsDeclaration() {
        final JavaClass javaClass = CLASSES.get(SourceFileResolver.class);

        final File file = unit.resolve(javaClass.getSourceCodeLocation());
        final int line = unit.lineNumber(javaClass, javaClass.getSourceCodeLocation(), file);

        Assertions.assertTrue(sourceLineOf(file, line).contains("class SourceFileResolver"),
            () -> "Expected the declaration, got line " + line + ": " + sourceLineOf(file, line));
    }

    @Test
    @DisplayName("Given a field, When resolving its line, Then the field declaration is reported")
    void resolvesAFieldToItsDeclaration() {
        final JavaClass javaClass = CLASSES.get(SourceFileResolver.class);
        final JavaField field = javaClass.getField("sourceRoots");

        final File file = unit.resolve(field.getSourceCodeLocation());
        final int line = unit.lineNumber(field, field.getSourceCodeLocation(), file);

        Assertions.assertTrue(sourceLineOf(file, line).contains("sourceRoots;"),
            () -> "Expected the declaration, got line " + line + ": " + sourceLineOf(file, line));
    }

    @Test
    @DisplayName("Given a source which cannot be read, When resolving, Then the reported line is kept")
    void fallsBackToTheReportedLine() {
        final JavaClass javaClass = CLASSES.get(NamedArchRule.class);
        final JavaMethod method = javaClass.getMethod("name");

        final int reported = Math.max(method.getSourceCodeLocation().getLineNumber(), 1);
        final int line = unit.lineNumber(method, method.getSourceCodeLocation(), new File("does-not-exist.java"));

        Assertions.assertEquals(reported, line);
    }

    private static String sourceLineOf(final File file, final int line) {
        try {
            return java.nio.file.Files.readAllLines(file.toPath()).get(line - 1);
        } catch (final Exception e) {
            return "<unreadable>";
        }
    }

    private static MavenProject project() {
        final MavenProject project = new MavenProject();

        project.setFile(WORKING_DIRECTORY.resolve("pom.xml").toFile());
        project.getCompileSourceRoots()
            .clear();
        project.addCompileSourceRoot(WORKING_DIRECTORY.resolve("src/main/java").toString());

        return project;
    }
}
