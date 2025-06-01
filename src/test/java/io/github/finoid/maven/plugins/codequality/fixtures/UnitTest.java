package io.github.finoid.maven.plugins.codequality.fixtures;

import au.com.origin.snapshots.Expect;
import au.com.origin.snapshots.junit5.SnapshotExtension;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.finoid.maven.plugins.codequality.fixtures.snapshot.JsonSnapshotSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Base test class for unit-related tests, providing JSON snapshot testing, and component setup.
 */
@Tag("UnitTest")
@ExtendWith({SnapshotExtension.class, MockitoExtension.class})
public class UnitTest {
    @SuppressWarnings("NullAway")
    protected Expect expect;

    /**
     * Takes a snapshot of the given object for regression testing.
     *
     * @param toBeSnapshotted the object to be snapshotted
     * @param <T>             the type of the object
     */
    @SuppressWarnings("SpellCheckingInspection")
    public <T> void snapshot(final T toBeSnapshotted) {
        snapshot(toBeSnapshotted, Collections.emptyList());
    }

    /**
     * Takes a snapshot of the given object with a specified filter.
     * The filter determines which fields are masked in the snapshot.
     *
     * @param toBeSnapshotted  the object to be snapshotted
     * @param maskedFieldPaths a list of field names to mask
     * @param <T>              the type of the object
     */
    @SuppressWarnings("SpellCheckingInspection")
    public <T> void snapshot(final T toBeSnapshotted, final List<String> maskedFieldPaths) {
        expect
            .serializer(new JsonSnapshotSerializer(maskedFieldPaths, createSimpleModule()))
            .toMatchSnapshot(toBeSnapshotted);
    }

    /**
     * Takes a snapshot of the given object with a specified filter.
     * The filter determines which fields are masked in the snapshot.
     *
     * @param toBeSnapshotted  the object to be snapshotted
     * @param maskedFieldPaths a list of field names to mask
     * @param <T>              the type of the object
     */
    @SuppressWarnings("SpellCheckingInspection")
    public <T> void snapshot(final T toBeSnapshotted, final String... maskedFieldPaths) {
        expect
            .serializer(new JsonSnapshotSerializer(Arrays.asList(maskedFieldPaths), createSimpleModule()))
            .toMatchSnapshot(toBeSnapshotted);
    }

    /**
     * Captures a snapshot of the given object within a specified test scenario.
     * This method is useful when running multiple variations of the same test case
     * and needing distinct snapshots for each scenario.
     *
     * @param toBeSnapshotted the object to be snapshotted
     * @param scenario        the name of the test scenario
     * @param <T>             the type of the object
     */
    @SuppressWarnings("SpellCheckingInspection")
    public <T> void snapshotScenario(final T toBeSnapshotted, final String scenario) {
        snapshotScenario(toBeSnapshotted, scenario, Collections.emptyList());
    }

    /**
     * Captures a snapshot of the given object within a specified test scenario, applying a field filter.
     * The filter determines which fields are masked or included in the snapshot.
     * This allows testing specific subsets of an object while maintaining scenario-based comparisons.
     *
     * @param toBeSnapshotted  the object to be snapshotted
     * @param scenario         the name of the test scenario
     * @param maskedFieldPaths a list of field names to mask
     * @param <T>              the type of the object
     */
    @SuppressWarnings("SpellCheckingInspection")
    public <T> void snapshotScenario(final T toBeSnapshotted, final String scenario, final List<String> maskedFieldPaths) {
        expect
            .serializer(new JsonSnapshotSerializer(maskedFieldPaths, createSimpleModule()))
            .scenario(scenario)
            .toMatchSnapshot(toBeSnapshotted);
    }

    /**
     * Creates a {@link SimpleModule} for customizing JSON serialization and deserialization.
     *
     * @return a new {@link SimpleModule} instance
     */
    protected SimpleModule createSimpleModule() {
        return new SimpleModule();
    }
}
