package io.github.finoid.maven.plugins.codequality.archunit;

import java.util.Collection;

/**
 * Supplies the ArchUnit rules a library wants applied to the projects consuming it.
 * <p>
 * Implementations are discovered through {@link java.util.ServiceLoader} from the test classpath of the analyzed
 * module, so a rule library declares itself by shipping a
 * {@code META-INF/services/io.github.finoid.maven.plugins.codequality.archunit.ArchRuleProvider} entry. Implementations
 * need a public no-args constructor.
 * <p>
 * A library which does not want a dependency on this plugin does not have to implement anything: rules can be
 * referenced directly from the plugin configuration instead, see {@code archUnit.rules}.
 */
public interface ArchRuleProvider {
    /**
     * The rules to apply.
     *
     * @return the rules, never null
     */
    Collection<NamedArchRule> rules();
}
