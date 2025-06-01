package io.github.finoid.maven.plugins.codequality.fixtures;

import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.Violation;
import io.github.finoid.maven.plugins.codequality.fixtures.checkstyle.ViolationFaker;

import java.util.Random;

public class AuditEventFaker implements Faker<AuditEvent> {
    private String fileName;
    private Violation violation;

    private final RandomGenerator randomGenerator;

    public AuditEventFaker(final Random random) {
        this.randomGenerator = RandomGenerator.of(random);

        this.fileName = randomGenerator.randomAlphanumeric(5);
        this.violation = ViolationFaker.violation(random).create();
    }

    @Override
    public AuditEvent create() {
        return new AuditEvent("", fileName, violation);
    }

    public AuditEventFaker withViolation(final Violation violation) {
        this.violation = violation;

        return this;
    }

    public AuditEventFaker withFileName(final String fileName) {
        this.fileName = fileName;

        return this;
    }

    public static AuditEventFaker auditEvent() {
        return new AuditEventFaker(new Random(1));
    }

    public static AuditEventFaker auditEvent(final Random random) {
        return new AuditEventFaker(random);
    }
}
