package io.github.finoid.maven.plugins.codequality.log;

import io.github.finoid.maven.plugins.codequality.exceptions.ParseException;
import io.github.finoid.maven.plugins.codequality.report.Violation;
import io.github.finoid.maven.plugins.codequality.report.ViolationLogParser;
import io.github.finoid.maven.plugins.codequality.step.ViolationConverter;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;

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
public class ErrorProneViolationLogParser implements ViolationLogParser {
    public static final Pattern VIOLATION_PATTERN =
        Pattern.compile("^(?<path>.*):\\[(?<line>\\d+)(?:,(?<column>\\d+))?\\] \\[(?<rule>.*)\\] (?<description>.*\\s*\\(.*\\s*.*)$");

    private static final Pattern PART_VIOLATION_PATTERN = Pattern.compile("^(.*):\\[(\\d+(?:,\\d+)?)\\] \\[(.*?)\\]");
    private static final Logger LOGGER = new ConsoleLogger(1, "console");
    private static final String THIRD_LINE_PREFIX = "  Did you mean";
    private static final int READ_AHEAD_LIMIT = 8 * 1024;

    private final ViolationConverter violationConverter;

    @Inject
    public ErrorProneViolationLogParser(final ViolationConverter violationConverter) {
        this.violationConverter = Precondition.nonNull(violationConverter, "ViolationConverter shouldn't be null");
    }

    @Override
    public List<Violation> parse(final InputStream fileInputStream) {
        final List<Violation> violations = new ArrayList<>();

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream, StandardCharsets.UTF_8))) {
            String nullableLine;

            while ((nullableLine = reader.readLine()) != null) {
                if (isNotErrorProneViolationLine(nullableLine)) {
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
        final StringBuilder errorProneViolationBuffer = new StringBuilder();

        errorProneViolationBuffer
            .append(currentLine)
            .append(System.lineSeparator()) // mimic the original output from error prone
            .append(reader.readLine());

        reader.mark(READ_AHEAD_LIMIT);

        final String peakAhead = reader.readLine();

        // optional third line: typically "  Did you mean ..."
        if (peakAhead != null && peakAhead.startsWith(THIRD_LINE_PREFIX)) {
            errorProneViolationBuffer.append(System.lineSeparator()); // mimic the original output from error prone
            errorProneViolationBuffer.append(peakAhead);
        } else {
            // Not a valid third line, rewind so the outer loop will process it
            reader.reset();
        }

        final Matcher violationMatcher = VIOLATION_PATTERN.matcher(errorProneViolationBuffer.toString());
        if (!violationMatcher.find()) {
            LOGGER.debug("Unexpected error prone log. Log: " + errorProneViolationBuffer);

            return Optional.empty();
        }

        return violationOf(errorProneViolationBuffer.toString());
    }

    private Optional<Violation> violationOf(final String errorProneViolationLines) {
        final Matcher violationMatcher = VIOLATION_PATTERN.matcher(errorProneViolationLines);

        if (!violationMatcher.find()) {
            LOGGER.debug("Unexpected error prone log. Log: " + errorProneViolationLines);

            return Optional.empty();
        }

        return Optional.of(violationConverter.ofErrorProneViolationMatcher(violationMatcher));
    }

    private static boolean isNotErrorProneViolationLine(final String nullableLine) {
        final Matcher partViolationMatcher = PART_VIOLATION_PATTERN.matcher(nullableLine);

        return !partViolationMatcher.find();
    }
}
