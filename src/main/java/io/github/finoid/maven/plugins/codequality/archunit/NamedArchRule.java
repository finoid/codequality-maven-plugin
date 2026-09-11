package io.github.finoid.maven.plugins.codequality.archunit;

import com.tngtech.archunit.lang.ArchRule;
import io.github.finoid.maven.plugins.codequality.util.Precondition;

/**
 * An {@link ArchRule} together with the stable identifier it is reported under.
 * <p>
 * The identifier ends up as the rule name of the emitted violation and as part of its fingerprint, so it must be
 * stable across builds. Prefer the name of the constant declaring the rule over the rule's own description, which
 * tends to be a long sentence and changes whenever the wording is improved.
 */
public record NamedArchRule(String name, ArchRule rule) {
    public NamedArchRule {
        Precondition.nonBlank(name, "Name shouldn't be blank");
        Precondition.nonNull(rule, "ArchRule shouldn't be null");
    }

    public static NamedArchRule of(final String name, final ArchRule rule) {
        return new NamedArchRule(name, rule);
    }
}
