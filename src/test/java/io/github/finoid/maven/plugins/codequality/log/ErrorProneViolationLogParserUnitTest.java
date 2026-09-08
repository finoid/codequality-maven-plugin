package io.github.finoid.maven.plugins.codequality.log;

import io.github.finoid.maven.plugins.codequality.exceptions.ParseException;
import io.github.finoid.maven.plugins.codequality.fixtures.ResourceUtils;
import io.github.finoid.maven.plugins.codequality.fixtures.TemplateResourceUtils;
import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import io.github.finoid.maven.plugins.codequality.step.ViolationConverter;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

class ErrorProneViolationLogParserUnitTest extends UnitTest {
    private static final Path PROJECT_ROOT_PATH = Paths.get("")
        .toAbsolutePath();

    @Mock
    private MavenSession session;
    @Mock
    private MavenExecutionRequest request;

    private ErrorProneViolationLogParser unit;

    @BeforeEach
    void beforeEach() {
        // Lenient, the parse of a closed stream never reaches the converter
        Mockito.lenient().when(session.getRequest())
            .thenReturn(request);
        Mockito.lenient().when(request.getMultiModuleProjectDirectory())
            .thenReturn(PROJECT_ROOT_PATH.toFile());

        unit = new ErrorProneViolationLogParser(new ViolationConverter(session));
    }

    @Test
    void givenValidLogFile_whenParse_thenExpectedViolations() {
        var logFileInputStream = ResourceUtils.tryInputStreamFrom("files/errorprone-logs.template.txt");

        var content = TemplateResourceUtils.template(logFileInputStream, PROJECT_ROOT_PATH.toString());

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