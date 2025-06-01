package io.github.finoid.maven.plugins.codequality.util;

import lombok.experimental.UtilityClass;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.twdata.maven.mojoexecutor.MojoExecutor;

import java.util.List;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

@UtilityClass
public class MojoUtils {
    @UtilityClass
    public static class ElementUtils {
        public static MojoExecutor.Element annotationProcessor(final String groupId, final String artifactId, final String version) {
            return element(name("path"),
                element(name("groupId"), groupId),
                element(name("artifactId"), artifactId),
                element(name("version"), version)
            );
        }
    }

    @UtilityClass
    public static class PluginUtils {
        public static PluginDescriptor pluginDescriptor(final String groupId, final String artifactId, final String version) {
            final PluginDescriptor descriptor = new PluginDescriptor();
            descriptor.setGroupId(groupId);
            descriptor.setArtifactId(artifactId);
            descriptor.setVersion(version);

            return descriptor;
        }

        public static Plugin pluginOfDescriptor(final PluginDescriptor descriptor) {
            return MojoExecutor.plugin(
                groupId(descriptor.getGroupId()),
                artifactId(descriptor.getArtifactId()),
                version(descriptor.getVersion())
            );
        }

        public static Plugin plugin(
            final String groupId,
            final String artifactId,
            final String version,
            final List<Dependency> dependencies
        ) {
            final Plugin plugin = new Plugin();
            plugin.setArtifactId(artifactId);
            plugin.setGroupId(groupId);
            plugin.setVersion(version);
            plugin.setDependencies(dependencies);

            return plugin;
        }
    }
}
