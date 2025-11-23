package io.github.finoid.maven.plugins.codequality.filter;

import lombok.Value;
import org.apache.maven.plugin.logging.Log;

public interface ViolationFilter {
    Violations filter(final Violations violations, final Context context);

    String name();

    @Value
    class Context {
        Log log;
    }
}
