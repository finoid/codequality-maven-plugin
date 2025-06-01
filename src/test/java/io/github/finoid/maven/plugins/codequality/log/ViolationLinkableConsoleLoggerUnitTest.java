package io.github.finoid.maven.plugins.codequality.log;

import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import io.github.finoid.maven.plugins.codequality.fixtures.ViolationFaker;
import org.junit.jupiter.api.Test;

class ViolationLinkableConsoleLoggerUnitTest extends UnitTest {
    private final ViolationLinkableConsoleLogger unit = new ViolationLinkableConsoleLogger();

    @Test
    void givenViolation_whenFormat_thenExpectedFormat() {
        var violation = ViolationFaker.violation()
            .create();

        var result = unit.format(violation);

        snapshot(result);
    }
}