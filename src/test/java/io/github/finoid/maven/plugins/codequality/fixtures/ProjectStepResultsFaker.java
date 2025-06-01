package io.github.finoid.maven.plugins.codequality.fixtures;

import io.github.finoid.maven.plugins.codequality.step.ProjectStepResults;
import io.github.finoid.maven.plugins.codequality.step.StepResult;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

public class ProjectStepResultsFaker implements Faker<ProjectStepResults> {
    String projectName;
    List<StepResult> results;

    public ProjectStepResultsFaker(final Random random) {
        final RandomGenerator randomGenerator = RandomGenerator.of(random);

        this.projectName = "Project " + randomGenerator.randomAlphanumeric(5);
        this.results = IntStream.range(1, randomGenerator.nextInt(2, 5))
            .mapToObj(it -> StepResultFaker.stepResultFaker(random).create())
            .toList();
    }

    @Override
    public ProjectStepResults create() {
        return new ProjectStepResults(projectName, results);
    }

    public ProjectStepResultsFaker withStepResult(final StepResult result) {
        this.results = List.of(result);

        return this;
    }

    public static List<ProjectStepResults> any() {
        final Random random = new Random(1);

        final RandomGenerator randomGenerator = RandomGenerator.of(random);

        final ProjectStepResultsFaker faker = new ProjectStepResultsFaker(random);

        return IntStream.range(1, randomGenerator.nextInt(2, 5))
            .mapToObj(it -> faker.create())
            .toList();
    }

    public static ProjectStepResultsFaker projectStepResults() {
        return new ProjectStepResultsFaker(new Random(1));
    }

    public static ProjectStepResultsFaker projectStepResults(final Random random) {
        return new ProjectStepResultsFaker(random);
    }
}
