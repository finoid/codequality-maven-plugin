package io.github.finoid.maven.plugins.codequality.filter;

import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import io.github.finoid.maven.plugins.codequality.fixtures.ViolationFaker;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class ViolationsFilterServiceUnitTest extends UnitTest {
    private static final Log LOG = new SystemStreamLog();

    @Test
    void givenNoFilters_whenFilter_thenViolationsAreReturnedUnchanged() {
        var violations = violations();
        var unit = new ViolationsFilterService(Collections.emptyList());

        var result = unit.filter(violations, context("ANY"));

        Assertions.assertSame(violations, result);
    }

    @Test
    void givenFilterNotConfigured_whenFilter_thenItIsNotApplied() {
        var applied = new ArrayList<String>();
        var unit = new ViolationsFilterService(List.of(recordingFilter("NOT_CONFIGURED", applied)));

        var violations = violations();
        var result = unit.filter(violations, context("CONFIGURED"));

        Assertions.assertEquals(Collections.emptyList(), applied);
        Assertions.assertSame(violations, result);
    }

    @Test
    void givenConfiguredFilters_whenFilter_thenOnlyThoseAreAppliedInOrder() {
        var applied = new ArrayList<String>();
        var unit = new ViolationsFilterService(List.of(
            recordingFilter("FIRST", applied),
            recordingFilter("SKIPPED", applied),
            recordingFilter("SECOND", applied)));

        unit.filter(violations(), context("FIRST", "SECOND"));

        Assertions.assertEquals(List.of("FIRST", "SECOND"), applied);
    }

    @Test
    void givenChainedFilters_whenFilter_thenEachReceivesTheResultOfThePrevious() {
        var unit = new ViolationsFilterService(List.of(
            droppingFilter("DROP_PERMISSIVE", true),
            droppingFilter("DROP_NON_PERMISSIVE", false)));

        var result = unit.filter(violations(), context("DROP_PERMISSIVE", "DROP_NON_PERMISSIVE"));

        Assertions.assertEquals(0, result.total());
    }

    private static Violations violations() {
        final Violation violation = ViolationFaker.violation().create();

        return new Violations(List.of(violation), List.of(violation));
    }

    private static ViolationsFilterService.Context context(final String... filterNames) {
        return new ViolationsFilterService.Context(LOG, Set.of(filterNames));
    }

    private static ViolationFilter recordingFilter(final String name, final List<String> applied) {
        return new ViolationFilter() {
            @Override
            public Violations filter(final Violations violations, final Context context) {
                applied.add(name);

                return violations;
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    /**
     * Empties one of the two buckets, so that chaining is observable in the result rather than only in a call order.
     */
    private static ViolationFilter droppingFilter(final String name, final boolean permissive) {
        return new ViolationFilter() {
            @Override
            public Violations filter(final Violations violations, final Context context) {
                return permissive
                    ? new Violations(Collections.emptyList(), violations.getNonPermissiveViolations())
                    : new Violations(violations.getPermissiveViolations(), Collections.emptyList());
            }

            @Override
            public String name() {
                return name;
            }
        };
    }
}
