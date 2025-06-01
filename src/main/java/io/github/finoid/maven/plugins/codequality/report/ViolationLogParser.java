package io.github.finoid.maven.plugins.codequality.report;

import io.github.finoid.maven.plugins.codequality.exceptions.ParseException;

import java.io.InputStream;
import java.util.List;

public interface ViolationLogParser {

    /**
     * Parses the provided input stream to extract a list of violations from log entries.
     *
     * @param fileInputStream the input stream containing the log data to be parsed.
     * @return a list of {@link Violation} objects representing the identified violations in the logs.
     * @throws ParseException if an error occurs during the parsing process, such as invalid log formatting or issues reading from the input stream.
     */
    List<Violation> parse(final InputStream fileInputStream);

}
