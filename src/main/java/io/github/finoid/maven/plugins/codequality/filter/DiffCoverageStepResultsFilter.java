package io.github.finoid.maven.plugins.codequality.filter;

import org.apache.maven.execution.MavenSession;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named("diff-coverage")
@Singleton
public class DiffCoverageStepResultsFilter implements ViolationFilter {
    public static final String NAME = "DIFF_COVERAGE";

    private final DiffCoverageFilter filter;
    private final MavenSession mavenSession;

    @Inject
    public DiffCoverageStepResultsFilter(final DiffCoverageFilter filter, final MavenSession mavenSession) {
        this.filter = filter;
        this.mavenSession = mavenSession;
    }

    @Override
    public Violations filter(final Violations violations, final Context context) {
        return filter.filterByDiffCoverage(
            violations,
            mavenSession.getRequest().getMultiModuleProjectDirectory().toPath(),
            context.getLog()
        );
    }

    @Override
    public String name() {
        return NAME;
    }
}
