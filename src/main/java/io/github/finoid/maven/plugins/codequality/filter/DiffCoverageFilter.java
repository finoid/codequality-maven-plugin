package io.github.finoid.maven.plugins.codequality.filter;

import io.github.finoid.maven.plugins.codequality.report.Violation;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectId;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Filters violations to only include those in modified or new lines based on git diff.
 * <p>
 * Supports:
 * - Diff between two refs (e.g. master..HEAD, master..customer)
 * - Optionally include local uncommitted changes (HEAD..working-tree)
 */
@Named
@Singleton
public class DiffCoverageFilter {
    /**
     * Filters violations based on the Git diff between the provided base and target references.
     * The diff mode controls whether to include committed changes, uncommitted changes,
     * or the union of both.
     *
     * @param violations      all violations detected by the analyzer
     * @param projectBasePath path to the project root containing the Git repository
     * @param log             Maven logger
     * @return a new {@link Violations} instance containing only violations in changed line ranges
     */
    public Violations filterByDiffCoverage(final Violations violations, final Path projectBasePath, final Log log) {
        // TODO (nw) use resolveBaseRef instead of HEAD?

        return filterByDiffCoverage(violations, projectBasePath, log, "refs/remotes/origin/HEAD", "HEAD", DiffMode.COMMITTED_PLUS_WORKING_TREE);
    }

    /**
     * Filters violations based on the Git diff between the provided base and target references.
     * The diff mode controls whether to include committed changes, uncommitted changes,
     * or the union of both.
     *
     * @param violations      all violations detected by the analyzer
     * @param projectBasePath path to the project root containing the Git repository
     * @param log             Maven logger
     * @param baseRef         the starting reference for diff (e.g. {@code origin/master}, {@code refs/heads/main})
     * @param targetRef       the ending reference for diff (e.g. {@code HEAD}, {@code refs/heads/customer})
     * @param mode            determines whether committed, uncommitted, or both types of changes are considered
     * @return a new {@link Violations} instance containing only violations in changed line ranges
     */
    public Violations filterByDiffCoverage(
        final Violations violations,
        final Path projectBasePath,
        final Log log,
        final String baseRef,
        final String targetRef,
        final DiffMode mode
    ) {
        try {
            final Map<String, List<LineRange>> changedLines = parseGitDiff(projectBasePath, baseRef, targetRef, mode);

            if (changedLines.isEmpty()) {
                log.info("No changed lines detected in git diff. All violations will be reported.");
                return violations;
            }

            final List<Violation> filteredPermissiveViolations = violations.getPermissiveViolations().stream()
                .filter(v -> isViolationInChangedLines(v, changedLines))
                .toList();

            final List<Violation> filteredNonPermissiveViolations = violations.getNonPermissiveViolations().stream()
                .filter(v -> isViolationInChangedLines(v, changedLines))
                .toList();

            final Violations filteredViolations = new Violations(filteredPermissiveViolations, filteredNonPermissiveViolations);

            log.info(String.format(
                "Diff coverage applied (%s %s..%s): %d/%d violations in modified lines",
                mode, baseRef, targetRef, filteredViolations.total(), violations.total()
            ));

            return filteredViolations;
        } catch (final Exception e) {
            log.warn(String.format("Failed to apply diff coverage: %s. Returning all violations.", e.getMessage()));
            return violations;
        }
    }

    private Map<String, List<LineRange>> parseGitDiff(
        final Path projectBasePath,
        final String baseRef,
        final String targetRef,
        final DiffMode mode
    ) throws IOException {
        final Map<String, List<LineRange>> changedLines = new HashMap<>();

        try (Git git = Git.open(projectBasePath.toFile())) {
            final Repository repository = git.getRepository();

            if (mode == DiffMode.COMMITTED_ONLY || mode == DiffMode.COMMITTED_PLUS_WORKING_TREE) {
                final AbstractTreeIterator baseTree = prepareTreeParser(repository, baseRef);
                final AbstractTreeIterator targetTree = prepareTreeParser(repository, targetRef);
                mergeChangedLines(changedLines, scanDiff(repository, baseTree, targetTree));
            }

            if (mode == DiffMode.WORKING_TREE_ONLY || mode == DiffMode.COMMITTED_PLUS_WORKING_TREE) {
                // Uncommitted local changes: HEAD tree -> working tree
                final AbstractTreeIterator headTree = prepareTreeParser(repository, "HEAD");
                final FileTreeIterator workingTree = new FileTreeIterator(repository);
                mergeChangedLines(changedLines, scanDiff(repository, headTree, workingTree));
            }
        }

        // Optional: normalize/merge overlapping ranges per file
        return normalizeRanges(changedLines);
    }

    private Map<String, List<LineRange>> scanDiff(
        final Repository repository,
        final AbstractTreeIterator oldTreeIterator,
        final AbstractTreeIterator newTreeIterator
    ) throws IOException {

        final Map<String, List<LineRange>> changedLines = new HashMap<>();

        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            diffFormatter.setContext(0); // --unified=0

            final List<DiffEntry> diffs = diffFormatter.scan(oldTreeIterator, newTreeIterator);

            for (final DiffEntry entry : diffs) {
                if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                    continue; // Skip deleted files
                }

                final String filePath = entry.getNewPath();
                final EditList edits = diffFormatter.toFileHeader(entry).toEditList();
                final List<LineRange> ranges = new ArrayList<>();

                for (final Edit edit : edits) {
                    // Only consider insertions and replacements (changes to new file)
                    if (edit.getType() != Edit.Type.DELETE) {
                        final int startLine = edit.getBeginB() + 1; // 1-based
                        final int endLine = edit.getEndB();         // inclusive end for our LineRange

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

        return changedLines;
    }

    private AbstractTreeIterator prepareTreeParser(final Repository repository, final String ref) throws IOException {
        final ObjectId objectId = repository.resolve(ref);
        if (objectId == null) {
            throw new IOException("Could not resolve git ref: " + ref);
        }

        try (RevWalk walk = new RevWalk(repository)) {
            final RevCommit commit = walk.parseCommit(objectId);
            final RevTree tree = walk.parseTree(commit.getTree().getId());

            final CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }
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
        for (LineRange range : ranges) {
            if (range.contains(line)) {
                return true;
            }
        }
        return false;
    }

    private void mergeChangedLines(final Map<String, List<LineRange>> into, final Map<String, List<LineRange>> from) {
        for (Map.Entry<String, List<LineRange>> e : from.entrySet()) {
            into.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
        }
    }

    /**
     * Sort and merge overlapping/adjacent ranges per file to keep the map compact and stable.
     */
    private Map<String, List<LineRange>> normalizeRanges(final Map<String, List<LineRange>> changedLines) {
        final Map<String, List<LineRange>> out = new HashMap<>();

        for (Map.Entry<String, List<LineRange>> e : changedLines.entrySet()) {
            final List<LineRange> ranges = new ArrayList<>(e.getValue());
            ranges.sort(Comparator.comparingInt(r -> r.start));

            final List<LineRange> merged = new ArrayList<>();
            for (LineRange r : ranges) {
                if (merged.isEmpty()) {
                    merged.add(r);
                } else {
                    LineRange last = merged.get(merged.size() - 1);
                    // merge overlaps or adjacency (end+1)
                    if (r.start <= last.end + 1) {
                        merged.set(merged.size() - 1, new LineRange(last.start, Math.max(last.end, r.end)));
                    } else {
                        merged.add(r);
                    }
                }
            }
            out.put(e.getKey(), merged);
        }

        return out;
    }

    @SuppressWarnings("UnusedMethod")
    private String resolveBaseRef(final Repository repository, final Log log) throws IOException {
        // 1. Try origin/HEAD (symbolic ref to main or master)
        final ObjectId originHead = repository.resolve("refs/remotes/origin/HEAD");
        if (originHead != null) {
            log.debug("Using base ref from origin/HEAD");
            return "refs/remotes/origin/HEAD";
        }

        // 2. Try origin/main
        if (repository.resolve("refs/remotes/origin/main") != null) {
            log.debug("Using base ref origin/main");
            return "refs/remotes/origin/main";
        }

        // 3. Try origin/master
        if (repository.resolve("refs/remotes/origin/master") != null) {
            log.debug("Using base ref origin/master");
            return "refs/remotes/origin/master";
        }

        // 4. Fallback to local main
        if (repository.resolve("refs/heads/main") != null) {
            log.debug("Using base ref main");
            return "refs/heads/main";
        }

        // 5. Fallback to local master
        if (repository.resolve("refs/heads/master") != null) {
            log.debug("Using base ref master");
            return "refs/heads/master";
        }

        log.warn("Could not resolve default base branch, falling back to HEAD");
        return "HEAD";
    }

    public enum DiffMode {
        /**
         * Only committed changes between baseRef and targetRef (tree..tree).
         */
        COMMITTED_ONLY,

        /**
         * Only uncommitted local changes (HEAD..working-tree).
         */
        WORKING_TREE_ONLY,

        /**
         * Union of committed changes (baseRef..targetRef) + uncommitted changes (HEAD..working-tree).
         */
        COMMITTED_PLUS_WORKING_TREE
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
