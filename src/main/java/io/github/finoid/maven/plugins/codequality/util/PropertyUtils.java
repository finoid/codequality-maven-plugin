package io.github.finoid.maven.plugins.codequality.util;

import lombok.experimental.UtilityClass;

import java.util.Properties;

@UtilityClass
public class PropertyUtils {

    public static String valueOrFallback(final Properties properties, final String property, final String fallback) {
        if (properties.containsKey(property)) {
            return properties.getProperty(property);
        }

        return properties.getProperty(property, fallback);
    }

}
