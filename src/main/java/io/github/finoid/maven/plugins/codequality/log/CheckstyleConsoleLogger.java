package io.github.finoid.maven.plugins.codequality.log;

import com.puppycrawl.tools.checkstyle.AuditEventFormatter;
import com.puppycrawl.tools.checkstyle.DefaultLogger;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import io.github.finoid.maven.plugins.codequality.util.Precondition;
import org.apache.maven.plugin.logging.Log;

import java.io.OutputStream;

/**
 * A logger for Checkstyle that integrates with the default Maven {@link Log} system instead of the
 * standard Checkstyle logger implementation.
 * <p>
 * This class extends {@link DefaultLogger} and formats audit events using the provided {@link AuditEventFormatter}.
 * It logs errors to both the default Checkstyle output stream and the Maven build logs through the provided {@link Log}.
 */
@SuppressWarnings("UnusedVariable") // TODO (nw) temporary
public class CheckstyleConsoleLogger extends DefaultLogger {
    private final AuditEventFormatter messageFormatter;
    private final Log log;

    public CheckstyleConsoleLogger(
        final OutputStream infoStream,
        final OutputStreamOptions infoStreamOptions,
        final OutputStream errorStream,
        final OutputStreamOptions errorStreamOptions,
        final AuditEventFormatter messageFormatter,
        final Log log
    ) {
        super(infoStream, infoStreamOptions, errorStream, errorStreamOptions, messageFormatter);

        this.messageFormatter = Precondition.nonNull(messageFormatter, "AuditEventFormatter shouldn't be null");
        this.log = Precondition.nonNull(log, "Log shouldn't be null");
    }

    @Override
    public void addException(final AuditEvent event, final Throwable throwable) {
        super.addException(event, throwable);

        this.log.error(throwable.getMessage());
    }


    @Override
    @SuppressWarnings("UnusedVariable")
    public void addError(final AuditEvent event) {
        super.addError(event);

        // TODO (nw) should only log if stepLogLevel is set?
        // log.info(messageFormatter.format(event));
    }
}
