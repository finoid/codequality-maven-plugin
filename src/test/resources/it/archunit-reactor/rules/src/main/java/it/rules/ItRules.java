package it.rules;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * Rules shipped by a library, referenced explicitly from the plugin configuration of the consuming module.
 */
public final class ItRules {
    public static final ArchRule NO_IMPL_SUFFIX = ArchRuleDefinition.classes()
        .should()
        .haveSimpleNameNotEndingWith("Impl")
        .allowEmptyShould(true);

    private ItRules() {
    }
}
