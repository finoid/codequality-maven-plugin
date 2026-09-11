package io.github.finoid.maven.plugins.codequality.archunit.fixture;

import io.github.finoid.maven.plugins.codequality.archunit.ArchRuleProvider;
import io.github.finoid.maven.plugins.codequality.archunit.NamedArchRule;

import java.util.Collection;
import java.util.List;

/**
 * A provider in the shape a rule library would ship.
 */
public class TestRuleProvider implements ArchRuleProvider {
    public static final String RULE_NAME = "PROVIDED_RULE";

    @Override
    public Collection<NamedArchRule> rules() {
        return List.of(NamedArchRule.of(RULE_NAME, TestRules.A_RULE));
    }
}
