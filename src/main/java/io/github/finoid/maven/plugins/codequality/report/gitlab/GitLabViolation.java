package io.github.finoid.maven.plugins.codequality.report.gitlab;

import lombok.Value;

@Value
public class GitLabViolation {
    String description;
    String fingerprint;
    String severity;
    Location location;

    @Value
    public static class Lines {
        Integer begin;
    }

    @Value
    public static class Location {
        String path;
        Lines lines;
    }
}
