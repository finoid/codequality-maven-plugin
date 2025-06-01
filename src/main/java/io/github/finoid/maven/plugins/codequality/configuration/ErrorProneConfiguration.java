package io.github.finoid.maven.plugins.codequality.configuration;

import lombok.Data;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.Collections;
import java.util.Set;

@Data
public class ErrorProneConfiguration implements Configuration {
    /**
     * Whether the error prone analyzer should be enabled or disabled.
     */
    @Parameter(property = "cq.errorprone.enabled")
    private boolean enabled = false;

    /**
     * Whether the null away analyzer should be enabled or disabled.
     */
    @Parameter(defaultValue = "cq.errorprone.nullAwayEnabled")
    private boolean nullAwayEnabled = false;

    /**
     * Whether the execution should be permissive (allow violations without failing) or strict (fail on violations).
     */
    @Parameter(property = "cq.errorprone.permissive")
    private boolean permissive = true;

    /**
     * The list of packages that should be considered properly annotated according to the NullAway convention.
     * See <a href="https://github.com/uber/NullAway/wiki/Configuration">NullAway Configuration</a>
     */
    @Parameter(defaultValue = "cq.errorprone.packages")
    private String annotatedPackages = "io.github.finoid";

    /**
     * The list of paths to be excluded.
     * See <a href="https://errorprone.info/docs/flags">ErrorProne flags</a>
     */
    @Parameter(defaultValue = "cq.errorprone.excludedPaths")
    private String excludedPaths = ".*/target/generated-sources/.*";

    /**
     * The list of custom compiler args.
     */
    @Parameter(defaultValue = "cq.errorprone.compilerArgs")
    private Set<String> compilerArgs = Collections.emptySet();

    /**
     * List of artifact dependency versions.
     */
    @Parameter
    private Versions versions = new Versions();

    public boolean isNotPermissive() {
        return !permissive;
    }

    @Data
    public static class Versions {
        @Parameter(defaultValue = "cq.errorprone.versions.errorprone")
        private String errorProne = "2.26.1";

        @Parameter(defaultValue = "cq.errorprone.versions.nullaway")
        private String nullAway = "0.10.25";
    }
}
