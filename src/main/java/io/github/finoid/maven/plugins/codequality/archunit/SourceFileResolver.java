package io.github.finoid.maven.plugins.codequality.archunit;

import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.SourceCodeLocation;
import com.tngtech.archunit.core.domain.properties.HasSourceCodeLocation;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Maps an ArchUnit source location back onto the source file and line it came from.
 *
 * <p>ArchUnit reads its locations from the bytecode, which carries the simple file name but not the path, so the path
 * is rebuilt from the package of the owning class and looked up under the source roots of the module. A class whose
 * file cannot be found - typically generated code, which has no source root - is reported against the module
 * directory rather than dropped, so the violation is still visible.
 *
 * <p>Line numbers need the same treatment for a different reason. The bytecode records the line of the first
 * statement of a method, and nothing at all for a class or a field, whereas a violation reads far better - and a
 * suppression comment can only sensibly be written - against the declaration. The declaration is therefore located in
 * the source, by scanning it. That is a heuristic, and a deliberate one: the class file simply does not record where
 * a declaration was, and parsing the source to find out would buy an accuracy nobody would notice at a cost nobody
 * wants. Every step falls back to what the bytecode said, so a scan which finds nothing never makes the location
 * worse than it would have been.
 */
public final class SourceFileResolver {
    private final List<Path> sourceRoots;
    private final Path baseDirectory;

    public SourceFileResolver(final MavenProject project, final boolean includeTestSources) {
        this.baseDirectory = project.getBasedir()
            .toPath();
        this.sourceRoots = sourceRootsOf(project, includeTestSources);
    }

    /**
     * The absolute path of the source file the location points at.
     *
     * @param location the ArchUnit source location
     * @return the resolved file, or the module directory when no source file matches
     */
    public File resolve(final SourceCodeLocation location) {
        final String relative = relativeSourcePath(location);

        for (final Path sourceRoot : sourceRoots) {
            final Path candidate = sourceRoot.resolve(relative);

            if (Files.isRegularFile(candidate)) {
                return candidate.toFile();
            }
        }

        return baseDirectory.toFile();
    }

    /**
     * The directory of the module, reported when a violation carries no source location at all.
     *
     * @return the module directory
     */
    public File moduleDirectory() {
        return baseDirectory.toFile();
    }

    /**
     * The line to report for the given element.
     *
     * @param element    the element the violation was reported against, a class or a member
     * @param location   the ArchUnit source location of that element
     * @param sourceFile the file the location was resolved to
     * @return the line number, never below one
     */
    public int lineNumber(final HasSourceCodeLocation element, final SourceCodeLocation location, final File sourceFile) {
        final int reported = Math.max(location.getLineNumber(), 1);
        final List<String> lines = linesOf(sourceFile);

        if (lines.isEmpty()) {
            return reported;
        }

        if (element instanceof JavaMember member) {
            return declarationLineOf(member, location, lines).orElse(reported);
        }

        return declarationLine(typeDeclaration(location.getSourceClass().getSimpleName()), lines, 0).orElse(reported);
    }

    /**
     * The line a member is declared on.
     *
     * <p>Searched backwards from the line the bytecode reported, being the first statement of the body, so the
     * nearest preceding declaration wins. Anything further up the file - an earlier overload, an unrelated call - is
     * therefore never reached. A field carries no line at all, so its whole file is searched instead.
     */
    private static Optional<Integer> declarationLineOf(final JavaMember member, final SourceCodeLocation location,
                                                                 final List<String> lines) {
        if (member instanceof JavaField field) {
            return declarationLine(fieldDeclaration(field.getName()), lines, 0);
        }

        final String name = member instanceof JavaConstructor
            ? member.getOwner().getSimpleName()
            : member.getName();

        return declarationLine(callableDeclaration(name), lines, Math.max(location.getLineNumber(), 1));
    }

    /**
     * The first line matching the given declaration.
     *
     * @param declaration the pattern identifying the declaration
     * @param lines       the lines of the source file
     * @param upperBound  the line to search backwards from, or zero to search the whole file forwards
     * @return the line, or empty when the source holds no such declaration
     */
    private static Optional<Integer> declarationLine(final Pattern declaration, final List<String> lines, final int upperBound) {
        if (upperBound > 0) {
            for (int line = Math.min(upperBound, lines.size()); line >= 1; line--) {
                if (declaration.matcher(lines.get(line - 1)).find()) {
                    return Optional.of(line);
                }
            }

            return Optional.empty();
        }

        for (int line = 1; line <= lines.size(); line++) {
            if (declaration.matcher(lines.get(line - 1)).find()) {
                return Optional.of(line);
            }
        }

        return Optional.empty();
    }

    /** A method or constructor, excluding calls to it, which are preceded by a dot. */
    private static Pattern callableDeclaration(final String name) {
        return Pattern.compile("(?<![.\\w])" + Pattern.quote(name) + "\\s*\\(");
    }

    /** A field, being its name followed by an initialiser, a terminator or a further declarator. */
    private static Pattern fieldDeclaration(final String name) {
        return Pattern.compile("(?<![.\\w])" + Pattern.quote(name) + "\\s*[;=,]");
    }

    private static Pattern typeDeclaration(final String simpleName) {
        return Pattern.compile("\\b(?:class|interface|enum|record|@interface)\\s+" + Pattern.quote(simpleName) + "\\b");
    }

    private static List<String> linesOf(final File sourceFile) {
        if (!sourceFile.isFile()) {
            return List.of();
        }

        try {
            return Files.readAllLines(sourceFile.toPath(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            // The location is best effort; an unreadable source must not fail the analysis
            return List.of();
        }
    }

    private static String relativeSourcePath(final SourceCodeLocation location) {
        final String packageName = location.getSourceClass()
            .getPackageName();

        if (packageName.isEmpty()) {
            return location.getSourceFileName();
        }

        return packageName.replace('.', File.separatorChar) + File.separatorChar + location.getSourceFileName();
    }

    private static List<Path> sourceRootsOf(final MavenProject project, final boolean includeTestSources) {
        final List<Path> roots = new ArrayList<>();

        addAll(roots, project.getCompileSourceRoots());

        if (includeTestSources) {
            addAll(roots, project.getTestCompileSourceRoots());
        }

        return roots;
    }

    private static void addAll(final List<Path> roots, final @Nullable List<String> sourceRoots) {
        if (sourceRoots == null) {
            return;
        }

        sourceRoots.stream()
            .map(Path::of)
            .filter(Files::isDirectory)
            .forEach(roots::add);
    }
}
