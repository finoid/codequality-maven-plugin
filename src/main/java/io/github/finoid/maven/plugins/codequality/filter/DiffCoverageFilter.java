package io.github.finoid.maven.plugins.codequality.filter;

import io.github.finoid.maven.plugins.codequality.report.Violation;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named
@Singleton
public class DiffCoverageFilter {
    /**
     * Filters violations to only include those in modified or new lines based on git diff.
     *
     * @param violations      the list of all violations
     * @param projectBasePath the base path of the project
     * @param log             the Maven log
     * @return filtered list of violations
     */
    public Violations filterByDiffCoverage(final Violations violations, final Path projectBasePath, final Log log) {
        try {
            final Map<String, List<LineRange>> changedLines = parseGitDiff(projectBasePath);

            if (changedLines.isEmpty()) {
                log.info("No changed lines detected in git diff. All violations will be reported.");
                return violations;
            }

            final List<Violation> filteredPermissiveViolations = violations.getPermissiveViolations().stream()
                .filter(violation -> isViolationInChangedLines(violation, changedLines))
                .toList();

            final List<Violation> filteredNonPermissiveViolations = violations.getNonPermissiveViolations().stream()
                .filter(violation -> isViolationInChangedLines(violation, changedLines))
                .toList();

            final Violations filteredViolations = new Violations(filteredPermissiveViolations, filteredNonPermissiveViolations);

            log.info(String.format("Diff coverage applied: %d/%d violations in modified lines", filteredViolations.total(), violations.total()));

            return filteredViolations;
        } catch (final Exception e) {
            log.warn(String.format("Failed to apply diff coverage: %s. Returning all violations.", e.getMessage()));
            return violations;
        }
    }

    private Map<String, List<LineRange>> parseGitDiff(final Path projectBasePath) throws IOException {
        final Map<String, List<LineRange>> changedLines = new HashMap<>();

        try (Git git = Git.open(projectBasePath.toFile())) {
            final Repository repository = git.getRepository();

            // Get HEAD tree
            final AbstractTreeIterator oldTreeIterator = prepareTreeParser(repository, "HEAD");

            // Get working tree
            final FileTreeIterator newTreeIterator = new FileTreeIterator(repository);

            // Create diff formatter
            try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(repository);
                diffFormatter.setContext(0); // Equivalent to --unified=0

                // Scan for differences
                final List<DiffEntry> diffs = diffFormatter.scan(oldTreeIterator, newTreeIterator);

                for (final DiffEntry entry : diffs) {
                    final String filePath = entry.getChangeType() == DiffEntry.ChangeType.DELETE
                        ? entry.getOldPath()
                        : entry.getNewPath();

                    if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                        continue; // Skip deleted files
                    }

                    // Get edit list for this file
                    final EditList edits = diffFormatter.toFileHeader(entry).toEditList();
                    final List<LineRange> ranges = new ArrayList<>();

                    for (final Edit edit : edits) {
                        // Only consider insertions and replacements (changes to new file)
                        if (edit.getType() != Edit.Type.DELETE) {
                            final int startLine = edit.getBeginB() + 1; // JGit uses 0-based indexing
                            final int endLine = edit.getEndB();

                            if (endLine > edit.getBeginB()) {
                                ranges.add(new LineRange(startLine, endLine));
                            }
                        }
                    }

                    if (!ranges.isEmpty()) {
                        changedLines.put(filePath, ranges);
                    }
                }
            }
        }

        return changedLines;
    }

    private AbstractTreeIterator prepareTreeParser(final Repository repository, final String ref) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            final RevCommit commit = walk.parseCommit(repository.resolve(ref));
            final RevTree tree = walk.parseTree(commit.getTree().getId());

            final CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }

            walk.dispose();
            return treeParser;
        }
    }

    private boolean isViolationInChangedLines(final Violation violation, final Map<String, List<LineRange>> changedLines) {
        final String relativePath = violation.getRelativePath();
        if (relativePath == null || violation.getLine() == null) {
            return false;
        }

        final List<LineRange> ranges = changedLines.get(relativePath);
        if (ranges == null) {
            return false;
        }

        final int line = violation.getLine();
        return ranges.stream()
            .anyMatch(range -> range.contains(line));
    }

    private static class LineRange {
        private final int start;
        private final int end;

        LineRange(final int start, final int end) {
            this.start = start;
            this.end = end;
        }

        boolean contains(final int line) {
            return line >= start && line <= end;
        }
    }
}
