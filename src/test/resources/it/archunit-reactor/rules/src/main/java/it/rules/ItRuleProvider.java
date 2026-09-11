package it.rules;

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.github.finoid.maven.plugins.codequality.archunit.ArchRuleProvider;
import io.github.finoid.maven.plugins.codequality.archunit.NamedArchRule;

import java.util.Collection;
import java.util.List;

/**
 * The same library registering a rule of its own, so a consuming module picks it up without configuring anything.
 */
public class ItRuleProvider implements ArchRuleProvider {
    @Override
    public Collection<NamedArchRule> rules() {
        return List.of(NamedArchRule.of("PROVIDED_NO_PUBLIC_MUTABLE_STATICS", ArchRuleDefinition.fields()
            .that()
            .areStatic()
            .and()
            .areNotFinal()
            .should()
            .notBePublic()
            .allowEmptyShould(true)));
    }
}
