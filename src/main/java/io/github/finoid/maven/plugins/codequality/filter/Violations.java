package io.github.finoid.maven.plugins.codequality.filter;

import io.github.finoid.maven.plugins.codequality.report.Violation;
import lombok.Value;

import java.util.List;

@Value
public class Violations {
    List<Violation> permissiveViolations;
    List<Violation> nonPermissiveViolations;

    public List<Violation> all() {
        return List.of(permissiveViolations, nonPermissiveViolations).stream()
            .flatMap(List::stream)
            .toList();
    }

    public int total() {
        return permissiveViolations.size() + nonPermissiveViolations.size();
    }
}
