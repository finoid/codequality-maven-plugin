# Finoid Code Quality maven plugin

A maven plugin that offers an integrated solution for enforcing code quality standards in your project.
It supports tools such as Checkstyle, Error Prone, NullAway, and SpotBugs, enabling automatic analysis of your codebase
to detect style violations and potential bugs early in the development process.

<div align="center">
  <img src=".github/assets/finoid-codequality-maven-plugin.jpg" width="256">
</div>

## Supported code quality tools

* Checkstyle – Analyzes Java code for style guideline violations, helping enforce consistent formatting and naming
  conventions.
* Error Prone – A static analysis tool from Google that catches common Java programming mistakes at compile time.
* NullAway – Detects and prevents NullPointerExceptions by enforcing null-safety contracts in your code.
* SpotBugs – Examines bytecode to identify a wide range of potential bugs, including performance issues, bad practices,
  and security flaws.

## Reports

The plugin supports reporting code quality violations to various outputs, such as the console or a JSON file. These
reports can help developers identify and address issues early in the build process or integrate with external systems.

| Reporter                            | Description                                                                                                                                                                                                |
|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **`ConsolePlainViolationReporter`** | Logs code quality violations to the Maven console using color-coded and linkable formatting for easy visibility during builds.                                                                             |
| **`ConsoleTableViolationReporter`** | Logs code quality violations to the Maven console using tables for easy visibility during builds.                                                                                                          |
| **`GitLabFileViolationReporter`**   | Serializes violations into a JSON file formatted for GitLab's [Code Quality widget](https://docs.gitlab.com/ee/user/project/merge_requests/code_quality.html), enabling inline feedback in merge requests. |

## Installation

You can use the Code Quality Maven Plugin in one of two ways:

### Run it directly from the command line

```bash
mvn io.github.finoid:codequality-maven-plugin:<latest>:code-quality
```

Replace <latest> with the current version of the plugin.

### Add it to your pom.xml

For continuous use across builds, include the plugin in your project’s pom.xml:

```xml

<plugin>
    <groupId>io.github.finoid</groupId>
    <artifactId>codequality-maven-plugin</artifactId>
    <version>${codequality-maven-plugin.version}</version>
    <executions>
        <execution>
            <id>maven-code-quality</id>
            <phase>validate</phase>
            <goals>
                <goal>code-quality</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <codeQuality>
            <enabled>true</enabled>
            <checkstyle>
                <executionMain>
                    <sourceDirectories>
                        <sourceDirectory>${project.basedir}/src/main/java</sourceDirectory>
                    </sourceDirectories>
                </executionMain>
                <executionTest>
                    <sourceDirectories>
                        <sourceDirectory>${project.basedir}/src/test/java</sourceDirectory>
                    </sourceDirectories>
                </executionTest>
            </checkstyle>
            <errorProne>
                <!-- Error prone is disabled by default -->
                <enabled>true</enabled>
            </errorProne>
            <checkerFramework>
                <!-- Checker framework is disabled by default -->
                <enabled>true</enabled>
            </checkerFramework>
        </codeQuality>
    </configuration>
</plugin>
```

### Configuration

| Parameter                  | Description                                                               | Default                                 |
|----------------------------|---------------------------------------------------------------------------|-----------------------------------------|
| `enabled`                  | Whether the code-quality analyzer should be enabled or disabled.          | `true`                                  |
| `annotationProcessorPaths` | List of annotation processor paths. Lombok will be automatically appended | `[]`                                    |
| `violationReporters`       | List of violation reporters.                                              | `[CONSOLE_PLAIN,GITLAB_FILE_VIOLATION]` |
| `violationFilters`         | List of violation filters.                                                | `[]`                                    |

### Checkstyle configuration

| Parameter       | Description                                         | Default |
|-----------------|-----------------------------------------------------|---------|
| `enabled`       | Whether the analyzer should be enabled or disabled. | `true`  |
| `consoleOutput` | Whether the logs should be output to the console.   | `true`  |

#### Execution Main

| Parameter             | Description                                                                 | Default                            |
|-----------------------|-----------------------------------------------------------------------------|------------------------------------|
| `enabled`             | Whether the analyzer should be enabled or disabled for `main`.              | `true`                             |
| `includes`            | Specifies the names filter of the source files to be used for Checkstyle.   | `**/*.java`                        |
| `resourceIncludes`    | Specifies the names filter of the resource files to be used for Checkstyle. | `**/*.properties`                  |
| `configLocation`      | The location of the checkstyle.xml.                                         | `checkstyle.xml`                   |
| `sourcesDirectory`    | The folder where Checkstyle shall run its checks.                           | `${project.build.sourceDirectory}` |
| `headerLocation`      | Location of the License file used to verify correct headers.                | `LICENSE.txt`                      |
| `suppressionLocation` | The location of the suppression file.                                       | `null`                             |
| `cacheFile`           | Specifies the cache file used to speed up Checkstyle on successive runs.    | `checkstyle-cachefile`             |
| `encoding`            | The file encoding used when reading the source files.                       | `project.build.sourceEncoding`     |

#### Execution Test

| Parameter             | Description                                                                      | Default                                |
|-----------------------|----------------------------------------------------------------------------------|----------------------------------------|
| `enabled`             | Whether the analyzer should be enabled or disabled for `test`.                   | `true`                                 |
| `includes`            | Specifies the names filter of the test source files to be used for Checkstyle.   | `**/*.java`                            |
| `resourceIncludes`    | Specifies the names filter of the test resource files to be used for Checkstyle. | `**/*.properties`                      |
| `configLocation`      | The location of the checkstyle.xml.                                              | `checkstyle.xml`                       |
| `sourcesDirectory`    | The test folder where Checkstyle shall run its checks.                           | `${project.build.testSourceDirectory}` |
| `headerLocation`      | Location of the License file used to verify correct headers.                     | `LICENSE.txt`                          |
| `suppressionLocation` | The location of the suppression file.                                            | `null`                                 |
| `cacheFile`           | Specifies the cache file used to speed up Checkstyle on successive runs.         | `checkstyle-test-cachefile`            |
| `encoding`            | The file encoding used when reading the test source files.                       | `project.build.sourceEncoding`         |

### ErrorProne configuration

| Parameter             | Description                                                                  | Default                          |
|-----------------------|------------------------------------------------------------------------------|----------------------------------|
| `enabled`             | Whether the Error Prone analyzer should be enabled.                          | `false`                          |
| `nullAwayEnabled`     | Whether the NullAway analyzer should be enabled.                             | `false`                          |
| `permissive`          | Whether the execution should be permissive (not fail on violations).         | `true`                           |
| `annotatedPackages`   | Packages considered properly annotated according to the NullAway convention. | `io.github.finoid`               |
| `excludedPaths`       | Paths to be excluded.                                                        | `.*/target/generated-sources/.*` |
| `compilerArgs`        | Custom compiler arguments.                                                   | `[]`                             |
| `versions.errorProne` | The Error Prone version to use.                                              | `2.26.1`                         |
| `versions.nullAway`   | The NullAway version to use.                                                 | `0.10.25`                        |

### CheckerFramework configuration

| Parameter                   | Description                                                          | Default                                                     |
|-----------------------------|----------------------------------------------------------------------|-------------------------------------------------------------|
| `enabled`                   | Whether the Checker Framework analyzer should be enabled.            | `false`                                                     |
| `permissive`                | Whether the execution should be permissive (not fail on violations). | `true`                                                      |
| `checkers`                  | The list of checkers to be run.                                      | See `CheckerFrameworkConfiguration` class in your codebase. |
| `compilerArgs`              | Custom compiler arguments.                                           | `[]`                                                        |
| `versions.checkerFramework` | The Checker Framework version to use.                                | `3.48.1`                                                    |
