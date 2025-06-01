package io.github.finoid.maven.plugins.codequality.fixtures;

import lombok.experimental.UtilityClass;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

@UtilityClass
public class TemplateResourceUtils {
    /**
     * Reads the content of the given template file and substitutes variables using {@link String#format(String, Object...)}.
     *
     * @param templateFile the input stream of the template file (not closed by this method)
     * @param vars         the variables to substitute into the template
     * @return the formatted string
     * @throws IOException if reading the template fails
     */
    public static String template(final InputStream templateFile, final Object... vars) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(templateFile))) {
            final String content = reader.lines()
                .collect(Collectors.joining(System.lineSeparator()));

            // Hacky template
            return String.format(content, vars);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
