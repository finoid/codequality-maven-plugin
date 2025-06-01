package io.github.finoid.maven.plugins.codequality.util;

import lombok.experimental.UtilityClass;
import org.codehaus.plexus.util.xml.Xpp3Dom;

@UtilityClass
public class ConfigurationUtils {
    public static String oneOrThrow(final Xpp3Dom configuration, final String key) {
        final Xpp3Dom[] children = configuration.getChildren(key);

        if (children.length != 1) {
            throw new IllegalStateException("Illegal configuration key provided");
        }

        return children[0].getValue();
    }
}
