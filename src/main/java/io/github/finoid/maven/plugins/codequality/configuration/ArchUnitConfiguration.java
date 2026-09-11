package io.github.finoid.maven.plugins.codequality.configuration;

import io.github.finoid.maven.plugins.codequality.report.Severity;
import lombok.Data;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Data
public class ArchUnitConfiguration implements Configuration {
    /**
     * Whether the ArchUnit analyzer should be enabled or disabled.
     */
    @Parameter(property = "cq.archunit.enabled")
    private boolean enabled = false;

    /**
     * Whether the execution should be permissive (allow violations without failing) or strict (fail on violations).
     */
    @Parameter(property = "cq.archunit.permissive")
    private boolean permissive = true;

    /**
     * Explicit rule references, evaluated in addition to whatever the service loader discovers.
     * <p>
     * Three forms are accepted:
     * <ul>
     *     <li>{@code com.example.MyRules#MY_RULE} - a static field of type {@code ArchRule}</li>
     *     <li>{@code com.example.MyRules#myRule()} - a static no-args method returning an {@code ArchRule}</li>
     *     <li>{@code com.example.MyRules} - every public static {@code ArchRule} field of the class, or, when the
     *     class implements {@link io.github.finoid.maven.plugins.codequality.archunit.ArchRuleProvider}, the rules
     *     it provides</li>
     * </ul>
     * <p>
     * The referenced classes are loaded from the test classpath of the analyzed module, so the rule library only
     * needs to be a test scoped dependency.
     */
    @Parameter(property = "cq.archunit.rules")
    private Set<String> rules = new LinkedHashSet<>();

    /**
     * Whether rule providers should be discovered through
     * {@link java.util.ServiceLoader} from the test classpath of the analyzed module.
     */
    @Parameter(property = "cq.archunit.serviceLoaderEnabled")
    private boolean serviceLoaderEnabled = true;

    /**
     * Whether the test classes of the module should be analyzed alongside its main classes.
     * <p>
     * Off by default: rules describing production structure tend to report the test fixtures which deliberately
     * violate them.
     */
    @Parameter(property = "cq.archunit.analyzeTestClasses")
    private boolean analyzeTestClasses = false;

    /**
     * The severity reported for a rule without an explicit entry in {@link #ruleSeverities}.
     * <p>
     * ArchUnit has no notion of severity of its own, so one has to be assigned here.
     */
    @Parameter(property = "cq.archunit.severity")
    private Severity severity = Severity.MAJOR;

    /**
     * Severity per rule name, overriding {@link #severity}.
     * <p>
     * Keyed by the name the rule is reported under, being the {@link
     * io.github.finoid.maven.plugins.codequality.archunit.NamedArchRule#name()} of a provided rule or the referenced
     * member for an explicitly configured one.
     */
    @Parameter
    private Map<String, Severity> ruleSeverities = new HashMap<>();

    public boolean isNotPermissive() {
        return !permissive;
    }

    /**
     * The severity to report the given rule under.
     *
     * @param ruleName the name the rule is reported under
     * @return the configured severity, or the step wide default
     */
    public Severity severityOf(final String ruleName) {
        return ruleSeverities.getOrDefault(ruleName, severity);
    }
}
