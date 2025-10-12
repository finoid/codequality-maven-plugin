package io.github.finoid.maven.plugins.codequality.configuration;

import lombok.Data;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.Collections;
import java.util.Set;

@Data
public class CheckerFrameworkConfiguration implements Configuration {
    /**
     * Whether the checker framework analyzer should be enabled or disabled.
     */
    @Parameter(property = "cq.checkerframework.enabled")
    private boolean enabled = false;

    /**
     * Whether the execution should be permissive (allow violations without failing) or strict (fail on violations).
     */
    @Parameter(property = "cq.checkerframework.permissive")
    private boolean permissive = true;

    /**
     * Set of source checkers to be used during the analyzing phase.
     * <p>
     * See <a href="https://github.com/typetools/checker-framework/tree/master/checker/src/main/java/org/checkerframework/checker">Checker framework</a> for a list of available checkers.
     */
    @Parameter(defaultValue = "cq.checkerframework.checkers")
    private Set<String> checkers = Set.of(
        "org.checkerframework.checker.regex.RegexChecker",
        "org.checkerframework.checker.formatter.FormatterChecker",
        "org.checkerframework.checker.tainting.TaintingChecker",
        "org.checkerframework.checker.calledmethods.CalledMethodsChecker",
        "org.checkerframework.checker.index.IndexChecker",
        "org.checkerframework.checker.interning.InterningChecker",
        "org.checkerframework.checker.mustcall.MustCallChecker",
        "org.checkerframework.checker.nonempty.NonEmptyChecker",
        "org.checkerframework.checker.optional.OptionalChecker",
        "org.checkerframework.checker.resourceleak.ResourceLeakChecker",
        "org.checkerframework.checker.sqlquotes.SqlQuotesChecker"
    );

    /**
     * Set of custom compiler arguments.
     */
    @Parameter(defaultValue = "cq.checkerframework.compilerArgs")
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
        @Parameter(defaultValue = "cq.checkerframework.versions.checkerFramework")
        private String checkerFramework = "3.48.1";
    }
}
