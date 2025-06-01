package io.github.finoid.maven.plugins.codequality.fixtures.checkstyle;

import com.puppycrawl.tools.checkstyle.api.SeverityLevel;
import com.puppycrawl.tools.checkstyle.api.Violation;
import io.github.finoid.maven.plugins.codequality.fixtures.Faker;
import io.github.finoid.maven.plugins.codequality.fixtures.RandomGenerator;

import java.util.Random;

public class ViolationFaker implements Faker<Violation> {
    private final int lineNumber;
    private final int columnNumber;
    private final String key;
    private final SeverityLevel severityLevel;
    private final String message;

    private final RandomGenerator randomGenerator;

    public ViolationFaker(final Random random) {
        this.randomGenerator = RandomGenerator.of(random);

        this.lineNumber = randomGenerator.nextInt(1, 1_00);
        this.columnNumber = randomGenerator.nextInt(1, 1_00);
        this.key = randomGenerator.randomAlphanumeric(5);
        this.severityLevel = randomGenerator.randomEnum(SeverityLevel.class);
        this.message = "Descriptive violation " + randomGenerator.randomAlphanumeric(5);
    }

    @Override
    public Violation create() {
        return new Violation(
            lineNumber,
            columnNumber,
            "",
            key,
            new Object[] {},
            severityLevel,
            "",
            null,
            message
        );
    }

    public static ViolationFaker violation() {
        return new ViolationFaker(new Random(1));
    }

    public static ViolationFaker violation(final Random random) {
        return new ViolationFaker(random);
    }
}
