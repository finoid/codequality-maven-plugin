package io.github.finoid.maven.plugins.codequality.report;

import lombok.Value;

@Value
public class Violation {
    String description;
    String fingerprint;
    Severity severity;
    String relativePath;
    String fullPath;
    Integer line;
    Integer columnNumber;
    String rule;
}
