package io.github.finoid.maven.plugins.codequality.archunit;

import com.tngtech.archunit.core.domain.SourceCodeLocation;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps an ArchUnit source location back onto the source file it came from.
 * <p>
 * ArchUnit reads its locations from the bytecode, which carries the simple file name and the line number but not the
 * path, so the path is rebuilt from the package of the owning class and looked up under the source roots of the
 * module. A class whose file cannot be found - typically generated code, which has no source root - is reported
 * against the module directory rather than dropped, so the violation is still visible.
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
     * The line to report, being the line of the location, or the first line when the bytecode carried none.
     * <p>
     * A location without a line number is normal for a violation reported against a class rather than a member.
     * Reporting line zero renders badly in the GitLab code quality widget, hence the fallback.
     *
     * @param location the ArchUnit source location
     * @return the line number, never below one
     */
    public int lineNumber(final SourceCodeLocation location) {
        return Math.max(location.getLineNumber(), 1);
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
