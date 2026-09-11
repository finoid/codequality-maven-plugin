package io.github.finoid.maven.plugins.codequality.archunit;

import io.github.finoid.maven.plugins.codequality.ExecutionContext;
import io.github.finoid.maven.plugins.codequality.archunit.fixture.TestRuleProvider;
import io.github.finoid.maven.plugins.codequality.configuration.ArchUnitConfiguration;
import io.github.finoid.maven.plugins.codequality.exceptions.CodeQualityException;
import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class ArchRuleResolverUnitTest extends UnitTest {
    private static final String RULES_CLASS = "io.github.finoid.maven.plugins.codequality.archunit.fixture.TestRules";
    private static final String PROVIDER_CLASS = "io.github.finoid.maven.plugins.codequality.archunit.fixture.TestRuleProvider";

    @Mock
    private Log log;

    private ArchRuleResolver unit;
    private ExecutionContext context;

    @BeforeEach
    void beforeEach() {
        unit = new ArchRuleResolver();
        context = ExecutionContext.of(new MavenProject(), log);
    }

    @Test
    @DisplayName("Given a static field reference, Then the rule is named SimpleClass.member so it is usable as a config key")
    void resolvesAStaticFieldReference() {
        final List<NamedArchRule> rules = resolve(RULES_CLASS + "#A_RULE");

        Assertions.assertEquals(1, rules.size());
        Assertions.assertEquals("TestRules.A_RULE", rules.getFirst().name());
    }

    @Test
    @DisplayName("Given a static method reference, Then the rule is resolved")
    void resolvesAStaticMethodReference() {
        final List<NamedArchRule> rules = resolve(RULES_CLASS + "#ruleFromAMethod()");

        Assertions.assertEquals(1, rules.size());
        Assertions.assertEquals("TestRules.ruleFromAMethod", rules.getFirst().name());
    }

    @Test
    @DisplayName("Given a bare class reference, Then every static rule field of the class is resolved")
    void resolvesEveryStaticFieldOfAClass() {
        final List<String> names = resolve(RULES_CLASS).stream()
            .map(NamedArchRule::name)
            .sorted()
            .toList();

        Assertions.assertEquals(List.of("TestRules.ANOTHER_RULE", "TestRules.A_RULE"), names);
    }

    @Test
    @DisplayName("Given a provider class reference, Then the rules it provides are resolved under their own names")
    void resolvesAProviderClassReference() {
        final List<NamedArchRule> rules = resolve(PROVIDER_CLASS);

        Assertions.assertEquals(1, rules.size());
        Assertions.assertEquals(TestRuleProvider.RULE_NAME, rules.getFirst().name());
    }

    @Test
    @DisplayName("Given the same rule referenced twice, Then it is evaluated once")
    void collapsesDuplicatesByName() {
        final ArchUnitConfiguration configuration = configuration();
        configuration.setRules(new LinkedHashSet<>(List.of(RULES_CLASS + "#A_RULE", RULES_CLASS)));

        final List<NamedArchRule> rules = unit.resolve(configuration, getClass().getClassLoader(), context);

        Assertions.assertEquals(2, rules.size(), "A_RULE resolved twice, ANOTHER_RULE once");
    }

    @Test
    @DisplayName("Given a member which is not a rule, Then the reference is rejected with the offending reference named")
    void rejectsAMemberWhichIsNotARule() {
        final CodeQualityException exception =
            Assertions.assertThrows(CodeQualityException.class, () -> resolve(RULES_CLASS + "#NOT_A_RULE"));

        Assertions.assertTrue(exception.getMessage().contains("NOT_A_RULE"), exception.getMessage());
        Assertions.assertTrue(exception.getMessage().contains("not a static field of type ArchRule"), exception.getMessage());
    }

    @Test
    @DisplayName("Given a method returning something else, Then the reference is rejected")
    void rejectsAMethodWhichDoesNotReturnARule() {
        final CodeQualityException exception =
            Assertions.assertThrows(CodeQualityException.class, () -> resolve(RULES_CLASS + "#notARule()"));

        Assertions.assertTrue(exception.getMessage().contains("not a static no-args method returning an ArchRule"), exception.getMessage());
    }

    @Test
    @DisplayName("Given an unknown class, Then the reference is rejected")
    void rejectsAnUnknownClass() {
        final CodeQualityException exception =
            Assertions.assertThrows(CodeQualityException.class, () -> resolve("com.example.DoesNotExist#RULE"));

        Assertions.assertTrue(exception.getMessage().contains("could not be loaded from the test classpath"), exception.getMessage());
    }

    @Test
    @DisplayName("Given no rules and no service loader, Then nothing is resolved rather than failing")
    void resolvesNothingWithoutConfiguration() {
        Assertions.assertTrue(unit.resolve(configuration(), getClass().getClassLoader(), context).isEmpty());
    }

    private List<NamedArchRule> resolve(final String... references) {
        final ArchUnitConfiguration configuration = configuration();
        configuration.setRules(new LinkedHashSet<>(Set.of(references)));

        return unit.resolve(configuration, getClass().getClassLoader(), context);
    }

    private static ArchUnitConfiguration configuration() {
        final ArchUnitConfiguration configuration = new ArchUnitConfiguration();
        // The plugin's own test classpath registers no provider; the explicit references are what is under test.
        configuration.setServiceLoaderEnabled(false);

        return configuration;
    }
}
