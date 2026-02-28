package io.github.finoid.maven.plugins.codequality.report;

import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import io.github.finoid.maven.plugins.codequality.exceptions.ParseException;
import io.github.finoid.maven.plugins.codequality.step.ViolationConverter;
import io.github.finoid.maven.plugins.codequality.util.Precondition;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class CheckerFrameworkViolationLogParser implements ViolationLogParser {
    public static final Pattern PART_VIOLATION_PATTERN = Pattern.compile("^(.*):(\\[\\d+,\\d+\\]).*(\\[.*\\])");
    public static final Pattern VIOLATION_PATTERN =
        Pattern.compile("^(?<path>.*):\\[(?<line>\\d+),(?<column>\\d+)\\].*\\[(?<rule>.*)\\] (?<description>(?s).*)$");

    private static final Logger LOGGER = new ConsoleLogger(1, "console");
    private static final int READ_AHEAD_LIMIT = 8 * 1024;

    private final ViolationConverter violationConverter;

    @Inject
    public CheckerFrameworkViolationLogParser(final ViolationConverter violationConverter) {
        this.violationConverter = Precondition.nonNull(violationConverter, "ViolationConverter shouldn't be null");
    }

    @Override
    public List<Violation> parse(final InputStream fileInputStream) {
        final List<Violation> violations = new ArrayList<>();

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream, StandardCharsets.UTF_8))) {
            String nullableLine;

            while ((nullableLine = reader.readLine()) != null) {
                if (isNotCheckerFrameworkViolationLine(nullableLine)) {
                    continue;
                }

                parseViolation(nullableLine, reader)
                    .ifPresent(violations::add);
            }

            return violations;
        } catch (final IOException e) {
            throw new ParseException("Exception during parsing", e);
        }
    }

    private Optional<Violation> parseViolation(final String currentLine, final BufferedReader reader) throws IOException {
        final StringBuilder checkerFrameworkViolationBuffer = new StringBuilder(currentLine);

        while (true) {
            reader.mark(READ_AHEAD_LIMIT);
            final String peakAhead = reader.readLine();
            if (peakAhead != null && peakAhead.startsWith("  ")) {
                checkerFrameworkViolationBuffer
                    .append(System.lineSeparator())
                    .append(peakAhead);
            } else {
                if (peakAhead != null) {
                    reader.reset();
                }
                break;
            }
        }

        return violationOf(checkerFrameworkViolationBuffer.toString());
    }

    private Optional<Violation> violationOf(final String checkerFrameworkViolationLines) {
        final Matcher violationMatcher = VIOLATION_PATTERN.matcher(checkerFrameworkViolationLines);

        if (!violationMatcher.find()) {
            LOGGER.warn("Unexpected checker framework log. Log: " + checkerFrameworkViolationLines);

            return Optional.empty();
        }

        return Optional.of(violationConverter.ofCheckerFrameworkViolationMatcher(violationMatcher));
    }

    private static boolean isNotCheckerFrameworkViolationLine(final String nullableLine) {
        final Matcher partViolationMatcher = PART_VIOLATION_PATTERN.matcher(nullableLine);

        return !partViolationMatcher.find();
    }

}
