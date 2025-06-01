package io.github.finoid.maven.plugins.codequality.step;

import io.github.finoid.maven.plugins.codequality.configuration.CodeQualityConfiguration;
import io.github.finoid.maven.plugins.codequality.configuration.Configuration;
import io.github.finoid.maven.plugins.codequality.exceptions.CodeQualityException;
import org.apache.maven.plugin.logging.Log;
import org.jspecify.annotations.Nullable;

public interface Step<C extends Configuration> {
    /**
     * Whether the step is enabled or disabled.
     *
     * @return true if enabled, false otherwise.
     */
    boolean isEnabled(final C configuration);

    /**
     * Whether the step has all prerequisites to run.
     *
     * @return the prerequisite result.
     */
    default PrerequisiteResult hasPrerequisites(final C configuration) {
        return PrerequisiteResult.OK;
    }

    /**
     * The type of the step.
     *
     * @return the step type
     */
    StepType type();

    /**
     * Executes the step.
     *
     * @param codeQualityConfiguration the code quality configuration
     * @param stepConfiguration        the step specific configuration
     * @throws CodeQualityException in case of an execution exception
     */
    StepResult execute(final CodeQualityConfiguration codeQualityConfiguration, final C stepConfiguration, final Log log);

    /**
     * Returns the {@link CleanContext} for the step.
     *
     * @return the clean configuration
     */
    CleanContext getCleanContext();

    record PrerequisiteResult(boolean hasAllPrerequisites, @Nullable String cause) {
        public static PrerequisiteResult OK = new PrerequisiteResult(true, null);

        public static PrerequisiteResult notOK(final String cause) {
            return new PrerequisiteResult(false, cause);
        }
    }
}
