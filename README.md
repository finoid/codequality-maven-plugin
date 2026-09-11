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

## Parallel builds

The `code-quality` goal is marked as thread safe, so a reactor can be built in parallel (`mvn -T ...`) without Maven
falling back to serializing the modules, and without warning about the goal.

The results of every module are collected and reported once, by the module which finishes last. In a parallel build that
is not necessarily the last module of the build order, so a module which is still running never loses its violations
from the aggregated report.

Note that the Checkstyle analysis itself runs one module at a time, even in a parallel build. Its executor is shared and
reconfigures the resolution of the configuration, header and suppression files for every module it is given. The
compiler based analyzers - Error Prone and the Checker Framework - keep running in parallel.

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
            <archUnit>
                <!-- ArchUnit is disabled by default -->
                <enabled>true</enabled>
                <rules>
                    <rule>com.example.arch.MyRules#NO_CYCLES</rule>
                </rules>
            </archUnit>
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

### ArchUnit configuration

Evaluates [ArchUnit](https://www.archunit.org/) rules against the compiled classes of the module and reports the
findings alongside the other analyzers, with the source file and line the violation belongs to.

Unlike the other analyzers the checks are not built in: the rules come from the project. Running them here rather than
as `@ArchTest` JUnit tests means they also run when the build skips tests, and that their findings reach the GitLab
code quality report. A project which keeps its ArchUnit tests should be aware the rules are then evaluated twice, once
by surefire and once here.

| Parameter              | Description                                                             | Default |
|------------------------|-------------------------------------------------------------------------|---------|
| `enabled`              | Whether the ArchUnit analyzer should be enabled.                        | `false` |
| `permissive`           | Whether the execution should be permissive (not fail on violations).    | `true`  |
| `rules`                | Explicit rule references, see below.                                    | `[]`    |
| `serviceLoaderEnabled` | Whether rule providers should be discovered from the test classpath.    | `true`  |
| `analyzeTestClasses`   | Whether the test classes should be analyzed alongside the main classes. | `false` |
| `severity`             | The severity reported for a rule without an entry in `ruleSeverities`.  | `MAJOR` |
| `ruleSeverities`       | Severity per rule name, overriding `severity`.                          | `{}`    |

#### Referencing rules explicitly

Three forms are accepted. The referenced classes are loaded from the **test** classpath, so the rule library only has
to be a test scoped dependency:

```xml
<archUnit>
    <enabled>true</enabled>
    <rules>
        <!-- a static ArchRule field -->
        <rule>com.example.arch.MyRules#NO_CYCLES</rule>
        <!-- a static no-args method returning an ArchRule -->
        <rule>com.example.arch.MyRules#noCycles()</rule>
        <!-- every public static ArchRule field of the class, or the rules of an ArchRuleProvider -->
        <rule>com.example.arch.MyRules</rule>
    </rules>
    <ruleSeverities>
        <MyRules.NO_CYCLES>BLOCKER</MyRules.NO_CYCLES>
    </ruleSeverities>
</archUnit>
```

A rule is reported under `SimpleClassName.member`, which is also the key `ruleSeverities` is looked up by. Neither `#`
nor the parentheses of a method reference are legal in an XML element name, hence the normalisation. An override
matching no resolved rule is warned about rather than silently ignored.

#### Providing rules from a library

A rule library can register itself instead, so consuming projects need no configuration beyond enabling the step.
Implement `ArchRuleProvider` and ship a service entry:

```java
public class MyRuleProvider implements ArchRuleProvider {
    @Override
    public Collection<NamedArchRule> rules() {
        return List.of(NamedArchRule.of("NO_CYCLES", MyRules.NO_CYCLES));
    }
}
```

```
META-INF/services/io.github.finoid.maven.plugins.codequality.archunit.ArchRuleProvider
```

Provider names are chosen by the library, so keep them usable as XML element names if consumers should be able to
override their severity.

#### Where the rules live

Both sources load from a jar just as happily as from the module's own classes, so a shared rule library can be wired
in two ways.

As a test scoped dependency of the analyzed module:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>arch-rules</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

Or as a dependency of the plugin declaration, which keeps it out of the project's own dependency tree entirely and
lets a parent POM hand the rules to every module that inherits it:

```xml
<plugin>
    <groupId>io.github.finoid</groupId>
    <artifactId>codequality-maven-plugin</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>arch-rules</artifactId>
            <version>1.0.0</version>
        </dependency>
    </dependencies>
</plugin>
```

Explicit references and service loader discovery work through either.

#### Dependency resolution scope

The goal keeps resolving dependencies in **compile** scope. Widening it to test scope would resolve the test
dependencies of every module whether or not this step is enabled, and would fail the goal on a test dependency which
cannot be resolved. The step resolves the test classpath itself, through `ProjectDependenciesResolver`, and only when
it actually runs.

#### Class loading

Rules are loaded through a class loader over the test classpath of the module, delegating to the plugin's own class
loader. ArchUnit therefore always resolves to the copy the plugin was built against. Rules compiled against another
1.x release link fine against it, since the types they touch (`ArchRule`, `ArchCondition`, `DescribedPredicate`) are
stable across the line, but a project on a future 2.x release would need the plugin upgraded in step.
