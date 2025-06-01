package io.github.finoid.maven.plugins.codequality;

import org.apache.maven.project.MavenProject;
import io.github.finoid.maven.plugins.codequality.configuration.AnnotationProcessorPaths;
import io.github.finoid.maven.plugins.codequality.configuration.CodeQualityConfiguration;
import io.github.finoid.maven.plugins.codequality.util.ProjectUtils;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MavenAnnotationProcessorsManager {
    private final Set<AnnotationProcessorPaths> annotationProcessorPaths;
    private final Set<String> annotationProcessors;

    public MavenAnnotationProcessorsManager(final MavenProject project, final CodeQualityConfiguration codeQualityConfiguration) {
        this.annotationProcessorPaths = annotationProcessorPathsOf(project, codeQualityConfiguration);
        this.annotationProcessors = annotationProcessorsOf(project);
    }

    public Set<AnnotationProcessorPaths> annotationPaths() {
        return annotationProcessorPaths;
    }

    public Set<String> annotationProcessors(final Set<String> additionalProcessors) {
        return Stream.concat(annotationProcessors.stream(), additionalProcessors.stream())
            .collect(Collectors.toSet());
    }

    private static Set<AnnotationProcessorPaths> annotationProcessorPathsOf(final MavenProject mavenProject, final CodeQualityConfiguration configuration) {
        if (ProjectUtils.isLombokPresentOnClassPath(mavenProject)) {
            final String artifactId = ProjectUtils.optionalArtifactVersion(mavenProject, "org.projectlombok", "lombok")
                .orElseThrow(() -> new IllegalStateException("Couldn't find lombok artifact id"));

            final AnnotationProcessorPaths lombokAnnotationProcessorPath =
                AnnotationProcessorPaths.create("org.projectlombok", "lombok", artifactId);

            return Stream.concat(configuration.getAnnotationProcessorPaths().stream(), Stream.of(lombokAnnotationProcessorPath))
                .collect(Collectors.toSet());
        }

        return configuration.getAnnotationProcessorPaths();
    }

    private static Set<String> annotationProcessorsOf(final MavenProject mavenProject) {
        if (ProjectUtils.isLombokPresentOnClassPath(mavenProject)) {
            return Set.of("lombok.launch.AnnotationProcessorHider$AnnotationProcessor");
        }

        return Collections.emptySet();
    }

}
