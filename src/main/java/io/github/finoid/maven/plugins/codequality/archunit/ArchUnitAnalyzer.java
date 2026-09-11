package io.github.finoid.maven.plugins.codequality.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.HasSourceCodeLocation;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.ViolationHandler;
import io.github.finoid.maven.plugins.codequality.ExecutionContext;
import io.github.finoid.maven.plugins.codequality.configuration.ArchUnitConfiguration;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.step.ViolationConverter;
import io.github.finoid.maven.plugins.codequality.util.Precondition;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates ArchUnit rules against the compiled classes of a module and turns the results into violations.
 * <p>
 * The classes are imported once and shared by every rule, since the import is by far the expensive part.
 */
@Singleton
public class ArchUnitAnalyzer {
    private final ViolationConverter violationConverter;

    @Inject
    public ArchUnitAnalyzer(final ViolationConverter violationConverter) {
        this.violationConverter = Precondition.nonNull(violationConverter, "ViolationConverter shouldn't be null");
    }

    /**
     * Evaluates the given rules.
     *
     * @param classDirectories the directories holding the compiled classes to analyze
     * @param rules            the rules to evaluate
     * @param configuration    the step configuration
     * @param context          the context of the current mojo execution
     * @return the violations found, empty when there is nothing to analyze
     */
    public List<Violation> analyze(final List<Path> classDirectories, final List<NamedArchRule> rules,
                                   final ArchUnitConfiguration configuration, final ExecutionContext context) {
        if (classDirectories.isEmpty()) {
            // Normally unreachable, the step declares the output directory as a prerequisite. Warned about rather
            // than debugged, so an empty analysis is never mistaken for a clean one.
            context.getLog()
                .warn("No compiled classes to analyze with ArchUnit, every rule will report nothing. Skipping...");

            return List.of();
        }

        final JavaClasses javaClasses = new ClassFileImporter().importPaths(classDirectories);
        final SourceFileResolver sourceFileResolver =
            new SourceFileResolver(context.getProject(), configuration.isAnalyzeTestClasses());

        final List<Violation> violations = new ArrayList<>();

        for (final NamedArchRule rule : rules) {
            violations.addAll(evaluate(rule, javaClasses, configuration, sourceFileResolver));
        }

        return violations;
    }

    private List<Violation> evaluate(final NamedArchRule namedRule, final JavaClasses javaClasses,
                                     final ArchUnitConfiguration configuration, final SourceFileResolver sourceFileResolver) {
        final EvaluationResult result = namedRule.rule()
            .evaluate(javaClasses);

        if (!result.hasViolation()) {
            return List.of();
        }

        final List<Violation> violations = new ArrayList<>();

        /*
         * An anonymous class rather than a lambda: ArchUnit derives the type it filters the corresponding objects by
         * from the reified varargs array, which a lambda does not provide. Typed as Object so every corresponding
         * object is handed over, whether it is a class, a member or an access.
         */
        result.handleViolations(new ViolationHandler<Object>() {
            @Override
            public void handle(final Collection<Object> correspondingObjects, final String message) {
                violations.add(toViolation(namedRule, correspondingObjects, message, configuration, sourceFileResolver));
            }
        });

        return violations;
    }

    private Violation toViolation(final NamedArchRule namedRule, final Collection<Object> correspondingObjects,
                                  final String message, final ArchUnitConfiguration configuration,
                                  final SourceFileResolver sourceFileResolver) {
        final Optional<HasSourceCodeLocation> element = locatableElement(correspondingObjects);

        final File file = element.map(it -> sourceFileResolver.resolve(it.getSourceCodeLocation()))
            .orElseGet(sourceFileResolver::moduleDirectory);
        final int line = element.map(it -> sourceFileResolver.lineNumber(it, it.getSourceCodeLocation(), file))
            .orElse(1);

        return violationConverter.ofArchUnitViolation(namedRule.name(), singleLine(message), file, line,
            configuration.severityOf(namedRule.name()));
    }

    /**
     * The element the violation is reported against, kept rather than only its location: a member knows its own name,
     * which is what lets the declaration be found in the source.
     */
    private static Optional<HasSourceCodeLocation> locatableElement(final Collection<Object> correspondingObjects) {
        return correspondingObjects.stream()
            .filter(HasSourceCodeLocation.class::isInstance)
            .map(HasSourceCodeLocation.class::cast)
            .findFirst();
    }

    /**
     * ArchUnit renders some violations over several lines. The report formats expect a single line, and the console
     * table renderer would break its layout on an embedded newline.
     */
    private static String singleLine(final String message) {
        return message.replace("\r", " ")
            .replace("\n", " ")
            .replaceAll("\\s{2,}", " ")
            .trim();
    }

}
