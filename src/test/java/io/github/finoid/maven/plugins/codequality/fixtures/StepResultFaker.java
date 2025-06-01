package io.github.finoid.maven.plugins.codequality.fixtures;

import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.step.StepResult;
import io.github.finoid.maven.plugins.codequality.step.StepType;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

public class StepResultFaker implements Faker<StepResult> {
    StepType type;
    List<Violation> violations;
    boolean isPermissive;

    public StepResultFaker(final Random random) {
        final RandomGenerator randomGenerator = RandomGenerator.of(random);

        this.type = randomGenerator.randomEnum(StepType.class);
        this.violations = IntStream.range(1, randomGenerator.nextInt(2, 5))
            .mapToObj(it -> ViolationFaker.violation(random).create())
            .toList();
        this.isPermissive = randomGenerator.nextBoolean();
    }

    @Override
    public StepResult create() {
        return StepResult.create(type, isPermissive, violations);
    }

    public StepResultFaker withViolation(final Violation violation) {
        this.violations = List.of(violation);

        return this;
    }

    public StepResultFaker withIsPermissive(final boolean isPermissive) {
        this.isPermissive = isPermissive;

        return this;
    }

    public static StepResultFaker stepResultFaker() {
        return new StepResultFaker(new Random(1));
    }

    public static StepResultFaker stepResultFaker(final Random random) {
        return new StepResultFaker(random);
    }
}
