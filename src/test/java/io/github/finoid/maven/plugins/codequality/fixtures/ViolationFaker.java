package io.github.finoid.maven.plugins.codequality.fixtures;

import io.github.finoid.maven.plugins.codequality.report.Severity;
import io.github.finoid.maven.plugins.codequality.report.Violation;

import java.util.Random;

public class ViolationFaker implements Faker<Violation> {
    private final String tool;
    private final String description;
    private final String fingerprint;
    private Severity severity;
    private final String relativePath;
    private final String fullPath;
    private final Integer line;
    private final RandomGenerator randomGenerator;
    private final Integer columnNumber;
    private final String rule;

    public ViolationFaker(final Random random) {
        this.randomGenerator = RandomGenerator.of(random);

        this.tool = randomGenerator.randomIn("ErrorProne", "Checkstyle", "CheckerFramework");
        this.description = "Description " + randomGenerator.randomAlphanumeric(10);
        this.fingerprint = randomGenerator.randomAlphanumeric(16);
        this.severity = randomGenerator.randomEnum(Severity.class);
        this.relativePath = "/file/" + randomGenerator.randomAlphanumeric(8) + ".java";
        this.fullPath = "/path/to/file/" + randomGenerator.randomAlphanumeric(8) + ".java";
        this.line = randomGenerator.nextInt(1, 100);
        this.columnNumber = randomGenerator.nextInt(1, 100);
        this.rule = randomGenerator.randomAlphanumeric(10);
    }

    @Override
    public Violation create() {
        return Violation.builder()
            .tool(tool)
            .description(description)
            .fingerprint(fingerprint)
            .severity(severity)
            .relativePath(relativePath)
            .fullPath(fullPath)
            .line(line)
            .columnNumber(columnNumber)
            .rule(rule)
            .build();
    }

    public ViolationFaker withSeverity(final Severity severity) {
        this.severity = severity;

        return this;
    }

    public static ViolationFaker violation() {
        return new ViolationFaker(new Random(1));
    }

    public static ViolationFaker violation(final Random random) {
        return new ViolationFaker(random);
    }

}
