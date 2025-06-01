package io.github.finoid.maven.plugins.codequality.configuration;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;
import io.github.finoid.maven.plugins.codequality.Environment;

import java.io.File;
import java.util.List;
import java.util.Optional;

@Data
public class CheckstyleConfiguration implements Configuration {
    /**
     * Whether the checkstyle analyzer should be enabled or disabled.
     */
    @Parameter(property = "cq.checkstyle.enabled")
    private boolean enabled = true;

    /**
     * Whether the logs should be output to console.
     */
    @Parameter(property = "cq.checkstyle.consoleOutput")
    private boolean consoleOutput = true;

    /**
     * Whether the execution should be permissive (allow violations without failing) or strict (fail on violations).
     */
    @Parameter(property = "cq.checkstyle.permissive")
    private boolean permissive = true;

    @Parameter
    private MainExecutionEnvironment executionMain = new MainExecutionEnvironment();

    @Parameter
    private TestExecutionEnvironment executionTest = new TestExecutionEnvironment();

    public boolean isNotPermissive() {
        return !permissive;
    }

    public interface ExecutionEnvironment {
        /**
         * Whether the checkstyle analyzer should be enabled or disabled for main.
         */
        boolean isEnabled();

        /**
         * Specifies the names filter of the source files to be used for Checkstyle.
         */
        String getIncludes();

        /**
         * Specifies the names filter of the resource files to be used for Checkstyle.
         */
        String getResourceIncludes();

        /**
         * <p>
         * Specifies the location of the XML configuration to use.
         * <p>
         * Potential values are a filesystem path, a URL, or a classpath resource.
         * This parameter expects that the contents of the location conform to the
         * xml format (Checkstyle <a
         * href="https://checkstyle.org/config.html#Modules">Checker
         * module</a>) configuration of rulesets.
         * <p>
         * This parameter is resolved as resource, URL, then file. If successfully
         * resolved, the contents of the configuration is copied into the
         * <code>${project.build.directory}/checkstyle-configuration.xml</code>
         * file before being passed to Checkstyle as a configuration.
         */
        String getConfigLocation();

        /**
         * Specifies the source directories to use when performing Checkstyle checks.
         * <p>
         * Potential values are a filesystem path and corresponds to
         * <a href="https://maven.apache.org/plugins/maven-checkstyle-plugin/check-mojo.html#sourceDirectories">sourceDirectories</a>
         * checkstyle plugin parameter.
         */
        List<File> getSourceDirectories();

        /**
         * <p>
         * Specifies the location of the License file (a.k.a. the header file) that
         * can be used by Checkstyle to verify that source code has the correct
         * license header.
         * <p>
         * You need to use <code>${checkstyle.header.file}</code> in your Checkstyle xml
         * configuration to reference the name of this header file.
         * <p>
         * For instance:
         * <pre>
         * &lt;module name="RegexpHeader"&gt;
         *   &lt;property name="headerFile" value="${checkstyle.header.file}"/&gt;
         * &lt;/module&gt;
         * </pre>
         */
        String getHeaderLocation();

        /**
         * <p>
         * Specifies the location of the suppressions XML file to use.
         * <p>
         * This parameter is resolved as resource, URL, then file. If successfully
         * resolved, the contents of the suppressions XML is copied into the
         * <code>${project.build.directory}/checkstyle-supressions.xml</code> file
         * before being passed to Checkstyle for loading.
         */
        Optional<String> optionalSuppressionLocation();

        /**
         * Specifies the cache file used to speed up Checkstyle on successive runs.
         */
        String getCacheFile();

        /**
         * The file encoding to use when reading the source files. If the property <code>project.build.sourceEncoding</code>
         * is not set, the platform default encoding is used. <strong>Note:</strong> This parameter always overrides the
         * property <code>charset</code> from Checkstyle's <code>TreeWalker</code> module.
         */
        String getEncoding();

        Environment getEnvironment();
    }

    @Data
    public static class MainExecutionEnvironment implements ExecutionEnvironment {
        @Parameter(property = "cq.checkstyle.main.enabled")
        private boolean enabled = true;

        @Parameter(property = "cq.checkstyle.main.includes")
        private String includes = "**\\/*.java";

        @Parameter(property = "cq.checkstyle.main.resourceIncludes")
        private String resourceIncludes = "**/*.properties";

        @Parameter(property = "cq.checkstyle.main.configLocation")
        private String configLocation = "checkstyle.xml";

        @Nullable
        @Parameter(property = "cq.checkstyle.main.sourceDirectories")
        private List<File> sourceDirectories;

        @Parameter(property = "cq.checkstyle.main.headerLocation")
        private String headerLocation = "LICENSE.txt";

        @Nullable
        @Getter(AccessLevel.NONE)
        @Parameter(property = "cq.checkstyle.main.suppressionLocation")
        private String suppressionLocation;

        @Parameter(property = "cq.checkstyle.main.cacheFile")
        private String cacheFile = "checkstyle-cachefile";

        @Parameter(property = "cq.checkstyle.main.encoding", defaultValue = "${project.build.sourceEncoding}")
        private String encoding = "${project.build.sourceEncoding}";

        @Override
        public Optional<String> optionalSuppressionLocation() {
            return Optional.ofNullable(suppressionLocation);
        }

        @Override
        public Environment getEnvironment() {
            return Environment.MAIN;
        }
    }

    @Data
    public static class TestExecutionEnvironment implements ExecutionEnvironment {
        @Parameter(property = "cq.checkstyle.test.enabled")
        private boolean enabled = true;

        @Parameter(property = "cq.checkstyle.test.includes")
        private String includes = "**\\/*.java";

        @Parameter(property = "cq.checkstyle.test.resourceIncludes")
        private String resourceIncludes = "**/*.properties";

        @Parameter(property = "cq.checkstyle.test.configLocation")
        private String configLocation = "checkstyle.xml";

        @Nullable
        @Parameter(property = "cq.checkstyle.test.sourceDirectories")
        private List<File> sourceDirectories;

        @Parameter(property = "cq.checkstyle.test.headerLocation")
        private String headerLocation = "LICENSE.txt";

        @Nullable
        @Getter(AccessLevel.NONE)
        @Parameter(property = "cq.checkstyle.test.suppressionLocation")
        private String suppressionLocation;

        @Parameter(property = "cq.checkstyle.test.cacheFile")
        private String cacheFile = "checkstyle-test-cachefile";

        @Parameter(property = "cq.checkstyle.test.encoding", defaultValue = "${project.build.sourceEncoding}")
        private String encoding = "${project.build.sourceEncoding}";

        @Override
        public Optional<String> optionalSuppressionLocation() {
            return Optional.ofNullable(suppressionLocation);
        }

        @Override
        public Environment getEnvironment() {
            return Environment.TEST;
        }
    }
}
