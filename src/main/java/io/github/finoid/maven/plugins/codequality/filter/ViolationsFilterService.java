package io.github.finoid.maven.plugins.codequality.filter;

import io.github.finoid.maven.plugins.codequality.util.Precondition;
import lombok.Value;
import org.apache.maven.plugin.logging.Log;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.Set;

@Named
@Singleton
public class ViolationsFilterService {
    private final List<ViolationFilter> filters;

    @Inject
    public ViolationsFilterService(final List<ViolationFilter> filters) {
        this.filters = Precondition.nonNull(filters, "ViolationFilters shouldn't be null");
    }

    public Violations filter(final Violations violations, final Context context) {
        if (filters.isEmpty()) {
            context.getLog()
                .info("No violation filters configured");

            return violations;
        }

        final ViolationFilter.Context violationFilterContext = new ViolationFilter.Context(context.getLog());

        Violations filteredViolations = violations;

        for (final ViolationFilter filter : filters) {
            if (!context.getFiltersByName().contains(filter.name())) {
                continue;
            }

            context.getLog()
                .info("Applying filter: " + filter.getClass().getSimpleName());

            filteredViolations = filter.filter(filteredViolations, violationFilterContext);
        }

        return filteredViolations;
    }

    @Value
    public static class Context {
        Log log;
        Set<String> filtersByName;
    }
}
