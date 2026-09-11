package io.github.finoid.maven.plugins.codequality.archunit.fixture;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * Rules referenced by the resolver tests, in each of the shapes the explicit configuration accepts.
 */
public final class TestRules {
    public static final ArchRule A_RULE = ArchRuleDefinition.classes()
        .should()
        .bePublic()
        .allowEmptyShould(true);

    public static final ArchRule ANOTHER_RULE = ArchRuleDefinition.classes()
        .should()
        .haveSimpleNameNotEndingWith("Impl")
        .allowEmptyShould(true);

    private static final String NOT_A_RULE = "ignored";

    private TestRules() {
    }

    public static ArchRule ruleFromAMethod() {
        return A_RULE;
    }

    public static String notARule() {
        return NOT_A_RULE;
    }
}
