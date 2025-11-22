package io.github.finoid.maven.plugins.codequality.filter;

import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;

import javax.inject.Inject;

@Component(role = ViolationFilter.class, hint = "diff-coverage")
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
