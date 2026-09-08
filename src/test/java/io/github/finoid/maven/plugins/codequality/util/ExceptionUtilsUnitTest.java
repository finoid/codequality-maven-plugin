package io.github.finoid.maven.plugins.codequality.util;

import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

class ExceptionUtilsUnitTest extends UnitTest {
    @Test
    void givenThrowableWithoutCause_whenRootCauseMessage_thenReturnsItsOwnMessage() {
        Assertions.assertEquals("Boom", ExceptionUtils.rootCauseMessage(new IllegalStateException("Boom")));
    }

    @Test
    void givenWrappedThrowable_whenRootCauseMessage_thenReturnsTheDeepestMessage() {
        var cause = new IllegalArgumentException("Unsupported class file major version 65");
        var wrapped = new RuntimeException("Compilation failure", cause);

        Assertions.assertEquals("Unsupported class file major version 65",
            ExceptionUtils.rootCauseMessage(new IllegalStateException("Unable to execute mojo", wrapped)));
    }

    @Test
    void givenDeepestCauseWithoutMessage_whenRootCauseMessage_thenReturnsTheDeepestAvailableMessage() {
        var cause = new IllegalStateException((String) null);
        var wrapped = new RuntimeException("Compilation failure", cause);

        Assertions.assertEquals("Compilation failure", ExceptionUtils.rootCauseMessage(wrapped));
    }

    @Test
    void givenNoMessageAnywhere_whenRootCauseMessage_thenReturnsTheDeepestTypeName() {
        // Note the explicit null message, RuntimeException(Throwable) derives one from the cause
        var wrapped = new RuntimeException(null, new IllegalArgumentException());

        Assertions.assertEquals("IllegalArgumentException", ExceptionUtils.rootCauseMessage(wrapped));
    }

    @Test
    void givenBlankMessage_whenRootCauseMessage_thenTreatedAsAbsent() {
        var wrapped = new RuntimeException("Compilation failure", new IllegalStateException("   "));

        Assertions.assertEquals("Compilation failure", ExceptionUtils.rootCauseMessage(wrapped));
    }

    @Test
    void givenCyclicCauseChain_whenRootCauseMessage_thenTerminates() {
        var first = new IllegalStateException("First");
        var second = new IllegalStateException("Second", first);
        first.initCause(second);

        // Which of the two the bounded traversal ends on is not interesting, that it ends at all is
        var result = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5), () -> ExceptionUtils.rootCauseMessage(first));

        Assertions.assertTrue(List.of("First", "Second").contains(result), () -> "Unexpected message: " + result);
    }
}
