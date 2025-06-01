package io.github.finoid.maven.plugins.codequality.configuration;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.maven.plugins.annotations.Parameter;

@Data
@SuppressWarnings("NullAway.Init")
@NoArgsConstructor // Required by org.codehaus.plexus.component.configurator.converters.AbstractConfigurationConverter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnnotationProcessorPaths {
    @Parameter
    private String groupId;

    @Parameter
    private String artifactId;

    @Parameter
    private String version;

    public static AnnotationProcessorPaths create(final String groupId, final String artifactId, final String version) {
        return new AnnotationProcessorPaths(groupId, artifactId, version);
    }
}
