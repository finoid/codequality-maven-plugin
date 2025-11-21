package io.github.finoid.maven.plugins.codequality;

import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.step.ProjectStepResults;
import io.github.finoid.maven.plugins.codequality.step.StepResult;
import io.github.finoid.maven.plugins.codequality.step.StepResults;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.BranchConfig;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Applies diff coverage over step results so that only the violations contained in changed files and lines remain.
 */
@Singleton
public class DiffCoverageApplier {
    private final MavenSession mavenSession;

    @Inject
    public DiffCoverageApplier(final MavenSession mavenSession) {
        this.mavenSession = mavenSession;
    }

    public StepResults apply(final StepResults stepResults, final Log log) {
        final Optional<Map<String, Set<Integer>>> changedLinesOptional = loadChangedLines(log);

        if (changedLinesOptional.isEmpty()) {
            return stepResults;
        }

        final Map<String, Set<Integer>> changedLines = changedLinesOptional.get();

        final List<ProjectStepResults> filteredProjectResults = stepResults.getResults().stream()
            .map(result -> filterProjectStepResults(result, changedLines))
            .filter(result -> !result.getResults().isEmpty())
            .toList();

        return StepResults.ofResults(filteredProjectResults);
    }

    private ProjectStepResults filterProjectStepResults(final ProjectStepResults projectStepResults,
                                                        final Map<String, Set<Integer>> changedLines) {
        final List<StepResult> filteredStepResults = projectStepResults.getResults().stream()
            .map(result -> filterStepResult(result, changedLines))
            .filter(result -> !result.getViolations().isEmpty())
            .toList();

        return new ProjectStepResults(projectStepResults.getProjectName(), filteredStepResults);
    }

    private StepResult filterStepResult(final StepResult stepResult, final Map<String, Set<Integer>> changedLines) {
        final List<Violation> filteredViolations = stepResult.getViolations().stream()
            .filter(violation -> isIncludedInDiff(violation, changedLines))
            .toList();

        return StepResult.create(stepResult.getType(), stepResult.isPermissive(), filteredViolations);
    }

    private boolean isIncludedInDiff(final Violation violation, final Map<String, Set<Integer>> changedLines) {
        if (violation.getRelativePath() == null || violation.getLine() == null) {
            return false;
        }

        final String normalizedPath = normalizePath(violation.getRelativePath());
        final Set<Integer> lines = changedLines.get(normalizedPath);

        return lines != null && lines.contains(violation.getLine());
    }

    private Optional<Map<String, Set<Integer>>> loadChangedLines(final Log log) {
        try (Repository repository = new FileRepositoryBuilder()
            .findGitDir(mavenSession.getTopLevelProject().getBasedir())
            .build();
             Git git = new Git(repository)) {

            final Optional<AbstractTreeIterator> oldTreeIterator = resolveOldTreeIterator(repository, log);
            final Optional<AbstractTreeIterator> newTreeIterator = resolveNewTreeIterator(repository, log);

            if (oldTreeIterator.isEmpty() || newTreeIterator.isEmpty()) {
                return Optional.empty();
            }

            final Map<String, Set<Integer>> changedLines = collectChangedLines(repository, git,
                oldTreeIterator.get(), newTreeIterator.get());

            return Optional.of(changedLines);
        } catch (final IOException | GitAPIException e) {
            log.warn(String.format("Unable to apply diff coverage. Cause: %s", e.getMessage()));

            return Optional.empty();
        }
    }

    private Map<String, Set<Integer>> collectChangedLines(final Repository repository, final Git git,
                                                          final AbstractTreeIterator oldTreeIterator,
                                                          final AbstractTreeIterator newTreeIterator)
        throws GitAPIException, IOException {

        final Map<String, Set<Integer>> changedLinesByFile = new HashMap<>();
        try (DiffFormatter diffFormatter = new DiffFormatter(OutputStream.nullOutputStream())) {
            diffFormatter.setRepository(repository);
            diffFormatter.setContext(0);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
            diffFormatter.setDetectRenames(true);

            final List<DiffEntry> diffEntries = git.diff()
                .setOldTree(oldTreeIterator)
                .setNewTree(newTreeIterator)
                .call();

            for (final DiffEntry entry : diffEntries) {
                if (!isSupportedChangeType(entry)) {
                    continue;
                }

                final String normalizedPath = normalizePath(entry.getNewPath());
                final EditList edits = diffFormatter.toFileHeader(entry).toEditList();

                for (final Edit edit : edits) {
                    if (edit.getType() == Edit.Type.DELETE) {
                        continue;
                    }

                    final Set<Integer> lines = changedLinesByFile.computeIfAbsent(normalizedPath, key -> new HashSet<>());
                    addChangedLines(lines, edit);
                }
            }
        }

        return changedLinesByFile;
    }

    private void addChangedLines(final Set<Integer> lines, final Edit edit) {
        for (int line = edit.getBeginB(); line < edit.getEndB(); line++) {
            lines.add(line + 1);
        }
    }

    private boolean isSupportedChangeType(final DiffEntry entry) {
        return Stream.of(DiffEntry.ChangeType.ADD, DiffEntry.ChangeType.MODIFY).anyMatch(type -> type == entry.getChangeType());
    }

    private Optional<AbstractTreeIterator> resolveOldTreeIterator(final Repository repository, final Log log)
        throws IOException {

        final Optional<ObjectId> upstreamCommit = resolveUpstreamCommit(repository, log);

        if (upstreamCommit.isEmpty()) {
            return Optional.empty();
        }

        final ObjectId headObjectId = repository.resolve(Constants.HEAD);
        if (headObjectId == null) {
            log.warn("Unable to resolve HEAD. Falling back to full results.");

            return Optional.empty();
        }

        try (RevWalk walk = new RevWalk(repository)) {
            final RevCommit headCommit = walk.parseCommit(headObjectId);
            final RevCommit upstreamHeadCommit = walk.parseCommit(upstreamCommit.get());
            walk.setRevFilter(RevFilter.MERGE_BASE);
            walk.markStart(headCommit);
            walk.markStart(upstreamHeadCommit);

            final RevCommit mergeBase = walk.next();
            final RevCommit baseCommit = mergeBase != null ? mergeBase : upstreamHeadCommit;

            return Optional.of(prepareTreeParser(repository, baseCommit));
        }
    }

    private Optional<AbstractTreeIterator> resolveNewTreeIterator(final Repository repository, final Log log) throws IOException {
        final ObjectId headId = repository.resolve(Constants.HEAD);

        if (headId == null) {
            log.warn("Unable to resolve HEAD. Falling back to full results.");

            return Optional.empty();
        }

        final RevCommit headCommit = resolveCommit(repository, headId);

        return Optional.of(prepareTreeParser(repository, headCommit));
    }

    private RevCommit resolveCommit(final Repository repository, final ObjectId objectId) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            return walk.parseCommit(objectId);
        }
    }

    private Optional<ObjectId> resolveUpstreamCommit(final Repository repository, final Log log) throws IOException {
        final String currentBranch = repository.getFullBranch();

        if (currentBranch == null) {
            log.warn("Unable to determine current branch. Falling back to full results.");

            return Optional.empty();
        }

        final BranchConfig branchConfig = new BranchConfig(repository.getConfig(), Repository.shortenRefName(currentBranch));
        final String trackingBranch = branchConfig.getTrackingBranch();

        if (trackingBranch == null) {
            log.warn("No upstream branch configured. Falling back to full results.");

            return Optional.empty();
        }

        final ObjectId upstreamHead = repository.resolve(trackingBranch);

        if (upstreamHead == null) {
            log.warn(String.format("Unable to resolve upstream branch %s. Falling back to full results.", trackingBranch));

            return Optional.empty();
        }

        return Optional.of(upstreamHead);
    }

    private AbstractTreeIterator prepareTreeParser(final Repository repository, final RevCommit commit) throws IOException {
        final RevTree tree;
        try (RevWalk walk = new RevWalk(repository)) {
            tree = walk.parseTree(commit.getTree().getId());
        }

        final CanonicalTreeParser parser = new CanonicalTreeParser();

        try (ObjectReader reader = repository.newObjectReader()) {
            parser.reset(reader, tree.getId());
        }

        return parser;
    }

    private String normalizePath(final String relativePath) {
        return Path.of(relativePath).normalize().toString().replace('\\', '/');
    }
}
