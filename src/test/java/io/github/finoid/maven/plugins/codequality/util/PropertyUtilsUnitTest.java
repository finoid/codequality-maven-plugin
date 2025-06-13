package io.github.finoid.maven.plugins.codequality.util;

import io.github.finoid.maven.plugins.codequality.fixtures.UnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Properties;

class PropertyUtilsUnitTest extends UnitTest {
    @Test
    void givenKeyExists_whenValueOrFallback_thenReturnsValue() {
        var properties = new Properties();
        properties.setProperty("some.key", "someValue");

        var result = PropertyUtils.valueOrFallback(properties, "some.key", "fallback");

        Assertions.assertEquals("someValue", result);
    }
}
