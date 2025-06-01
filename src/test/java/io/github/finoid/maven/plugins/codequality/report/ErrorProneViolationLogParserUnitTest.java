package io.github.finoid.maven.plugins.codequality.report;

import io.github.finoid.maven.plugins.codequality.exceptions.ParseException;
import io.github.finoid.maven.plugins.codequality.fixtures.ResourceUtils;
import io.github.finoid.maven.plugins.codequality.fixtures.TemplateResourceUtils;
import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import io.github.finoid.maven.plugins.codequality.step.ViolationConverter;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Paths;

class ErrorProneViolationLogParserUnitTest extends UnitTest {
    @Mock
    private MavenProject project;

    private ErrorProneViolationLogParser unit;

    @BeforeEach
    void beforeEach() {
        unit = new ErrorProneViolationLogParser(new ViolationConverter(project));
    }

    @Test
    void givenValidLogFile_whenParse_thenExpectedViolations() {
        var logFileInputStream = ResourceUtils.tryInputStreamFrom("files/errorprone-logs.template.txt");

        var projectRootPath = Paths.get("")
            .toAbsolutePath();

        var content = TemplateResourceUtils.template(logFileInputStream, projectRootPath.toString());

        Mockito.when(project.getBasedir())
            .thenReturn(projectRootPath.toFile());

        var result = unit.parse(new ByteArrayInputStream(content.getBytes()));

        snapshot(result, "*..fullPath");
    }

    @Test
    void givenClosedInputStream_whenParse_thenExpectedException() throws IOException {
        var logFileInputStream = ResourceUtils.tryInputStreamFrom("files/errorprone-logs.template.txt");
        logFileInputStream.close();

        Assertions.assertThrows(ParseException.class, () -> unit.parse(logFileInputStream));
    }

}