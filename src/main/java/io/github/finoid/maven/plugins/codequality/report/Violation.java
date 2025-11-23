package io.github.finoid.maven.plugins.codequality.report;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Violation {
    String tool;
    String description;
    String fingerprint;
    Severity severity;
    String relativePath;
    String fullPath;
    Integer line;
    Integer columnNumber;
    String rule;
}
