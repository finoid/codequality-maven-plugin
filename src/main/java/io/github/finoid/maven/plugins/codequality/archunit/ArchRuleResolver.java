package io.github.finoid.maven.plugins.codequality.archunit;

import com.tngtech.archunit.lang.ArchRule;
import io.github.finoid.maven.plugins.codequality.ExecutionContext;
import io.github.finoid.maven.plugins.codequality.configuration.ArchUnitConfiguration;
import io.github.finoid.maven.plugins.codequality.exceptions.CodeQualityException;

import javax.inject.Singleton;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Resolves the rules to evaluate, from the plugin configuration and from the service loader.
 * <p>
 * Both sources are read through a class loader over the test classpath of the analyzed module, so a rule library only
 * has to be a test scoped dependency of the project. That class loader delegates to the class loader of this plugin,
 * which means ArchUnit itself is always the copy this plugin was built against, even when the project depends on a
 * different version. Rules compiled against another 1.x release link fine against it, since the types they touch
 * ({@code ArchRule}, {@code ArchCondition}, {@code DescribedPredicate}) are stable across the line, but a project on a
 * 2.x release would need this plugin to be upgraded in step.
 */
@Singleton
public class ArchRuleResolver {
    private static final String MEMBER_SEPARATOR = "#";
    private static final String METHOD_SUFFIX = "()";

    /**
     * Resolves every configured and discovered rule.
     * <p>
     * Duplicates by name are collapsed, so a rule both provided through the service loader and referenced explicitly
     * is evaluated once.
     *
     * @param configuration the step configuration
     * @param classLoader   the class loader over the test classpath of the analyzed module
     * @param context       the context of the current mojo execution
     * @return the rules to evaluate, in a stable order
     */
    public List<NamedArchRule> resolve(final ArchUnitConfiguration configuration, final ClassLoader classLoader,
                                       final ExecutionContext context) {
        final Map<String, NamedArchRule> byName = new LinkedHashMap<>();

        if (configuration.isServiceLoaderEnabled()) {
            fromServiceLoader(classLoader, context).forEach(rule -> byName.putIfAbsent(rule.name(), rule));
        }

        for (final String reference : configuration.getRules()) {
            fromReference(reference, classLoader).forEach(rule -> byName.putIfAbsent(rule.name(), rule));
        }

        return new ArrayList<>(byName.values());
    }

    private List<NamedArchRule> fromServiceLoader(final ClassLoader classLoader, final ExecutionContext context) {
        final List<NamedArchRule> rules = new ArrayList<>();

        try {
            for (final ArchRuleProvider provider : ServiceLoader.load(ArchRuleProvider.class, classLoader)) {
                rules.addAll(provider.rules());
            }
        } catch (final ServiceConfigurationError e) {
            // A broken provider on the classpath must not take the build down; the explicitly configured rules are
            // still worth evaluating.
            context.getLog()
                .warn(String.format("Failed to load an ArchRuleProvider. Cause: %s", e.getMessage()));
        }

        return rules;
    }

    private List<NamedArchRule> fromReference(final String reference, final ClassLoader classLoader) {
        final int separator = reference.indexOf(MEMBER_SEPARATOR);

        if (separator < 0) {
            return fromClass(reference, classLoader);
        }

        final String className = reference.substring(0, separator);
        final String memberName = reference.substring(separator + 1);
        final Class<?> owner = loadClass(className, classLoader, reference);

        if (memberName.endsWith(METHOD_SUFFIX)) {
            final String methodName = memberName.substring(0, memberName.length() - METHOD_SUFFIX.length());

            return List.of(fromMethod(owner, methodName, className, reference));
        }

        return List.of(fromField(owner, memberName, className, reference));
    }

    private List<NamedArchRule> fromClass(final String className, final ClassLoader classLoader) {
        final Class<?> type = loadClass(className, classLoader, className);

        if (ArchRuleProvider.class.isAssignableFrom(type)) {
            return new ArrayList<>(instantiateProvider(type, className).rules());
        }

        final List<NamedArchRule> rules = new ArrayList<>();

        for (final Field field : type.getDeclaredFields()) {
            if (isStaticArchRule(field)) {
                rules.add(fromField(type, field.getName(), className, className + MEMBER_SEPARATOR + field.getName()));
            }
        }

        if (rules.isEmpty()) {
            throw new CodeQualityException(String.format(
                "ArchUnit rule reference [%s] resolved to a class with neither an ArchRuleProvider implementation nor a"
                    + " static ArchRule field", className));
        }

        return rules;
    }

    private NamedArchRule fromField(final Class<?> owner, final String fieldName, final String className, final String reference) {
        try {
            final Field field = owner.getDeclaredField(fieldName);

            if (!isStaticArchRule(field)) {
                throw new CodeQualityException(String.format(
                    "ArchUnit rule reference [%s] is not a static field of type ArchRule", reference));
            }

            field.setAccessible(true);

            return NamedArchRule.of(nameOf(className, fieldName), (ArchRule) field.get(null));
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            throw new CodeQualityException(String.format("Failed to read ArchUnit rule [%s]. Cause: %s", reference, e.getMessage()), e);
        }
    }

    private NamedArchRule fromMethod(final Class<?> owner, final String methodName, final String className, final String reference) {
        try {
            final Method method = owner.getDeclaredMethod(methodName);

            if (!Modifier.isStatic(method.getModifiers()) || !ArchRule.class.isAssignableFrom(method.getReturnType())) {
                throw new CodeQualityException(String.format(
                    "ArchUnit rule reference [%s] is not a static no-args method returning an ArchRule", reference));
            }

            method.setAccessible(true);

            return NamedArchRule.of(nameOf(className, methodName), (ArchRule) method.invoke(null));
        } catch (final ReflectiveOperationException e) {
            throw new CodeQualityException(String.format("Failed to invoke ArchUnit rule [%s]. Cause: %s", reference, e.getMessage()), e);
        }
    }

    private ArchRuleProvider instantiateProvider(final Class<?> type, final String reference) {
        try {
            return (ArchRuleProvider) type.getDeclaredConstructor()
                .newInstance();
        } catch (final ReflectiveOperationException e) {
            throw new CodeQualityException(
                String.format("Failed to instantiate ArchRuleProvider [%s]. Cause: %s", reference, e.getMessage()), e);
        }
    }

    private Class<?> loadClass(final String className, final ClassLoader classLoader, final String reference) {
        try {
            return Class.forName(className, true, classLoader);
        } catch (final ClassNotFoundException e) {
            throw new CodeQualityException(String.format(
                "ArchUnit rule reference [%s] could not be loaded from the test classpath of the module", reference), e);
        }
    }

    private static boolean isStaticArchRule(final Field field) {
        return Modifier.isStatic(field.getModifiers()) && ArchRule.class.isAssignableFrom(field.getType());
    }

    /**
     * The reported name of a rule, being the referenced member qualified by the simple name of its class.
     * <p>
     * Neither {@code #} nor the parentheses of a method reference survive as an XML element name, and the severity
     * overrides are keyed by rule name in the plugin configuration, so the reference is normalised to
     * {@code SimpleClassName.member} - which is both a legal element name and shorter to read in a report.
     */
    private static String nameOf(final String className, final String memberName) {
        final int lastDot = className.lastIndexOf('.');
        final String simpleName = lastDot < 0 ? className : className.substring(lastDot + 1);

        return simpleName + '.' + memberName;
    }
}
