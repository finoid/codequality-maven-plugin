package io.github.finoid.maven.plugins.codequality.log;

import com.puppycrawl.tools.checkstyle.AuditEventFormatter;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;

import java.util.Locale;

/**
 * Formats audit event messages to make them linkable in the console output.
 * <p>
 * This class modifies the {@link com.puppycrawl.tools.checkstyle.AuditEventDefaultFormatter}
 * by enhancing the formatting of {@link AuditEvent} to include clickable file paths and
 * precise line/column information, making it easier to navigate to specific issues in the console.
 * <p>
 * The formatted message includes the file name, line and column numbers, severity level, and the
 * name of the Checkstyle module (optionally omitting the "Check" suffix for brevity).
 * </p>
 */
public class LinkableAuditEventDefaultFormatter implements AuditEventFormatter {
    /**
     * Length of all separators.
     */
    private static final int LENGTH_OF_ALL_SEPARATORS = 10;

    /**
     * Suffix of module names like XXXXCheck.
     */
    private static final String SUFFIX = "Check";

    @Override
    public String format(final AuditEvent event) {
        final String fileName = event.getFileName();
        final String message = event.getMessage();

        final String severityLevelName = severityLevelNameOf(event);

        final StringBuilder sb = initStringBuilderWithOptimalBuffer(event, severityLevelName);

        sb.append(fileName)
            .append(':')
            .append('[')
            .append(event.getLine())
            .append(',')
            .append(event.getColumn())
            .append("] ")
            .append(message)
            .append(" [");

        if (event.getModuleId() == null) {
            final String checkShortName = getCheckShortName(event);
            sb.append(checkShortName);
        } else {
            sb.append(event.getModuleId());
        }

        sb.append(']');

        return sb.toString();
    }

    private static String severityLevelNameOf(final AuditEvent auditEvent) {
        return auditEvent.getSeverityLevel()
            .getName()
            .toUpperCase(Locale.US);
    }

    /**
     * Returns the StringBuilder that should avoid StringBuffer.expandCapacity.
     * bufferLength = fileNameLength + messageLength + lengthOfAllSeparators +
     * + severityNameLength + checkNameLength.
     * Method is excluded from pitest validation.
     *
     * @param event             audit event.
     * @param severityLevelName severity level name.
     * @return optimal StringBuilder.
     */
    private static StringBuilder initStringBuilderWithOptimalBuffer(final AuditEvent event, final String severityLevelName) {
        final int bufLen = LENGTH_OF_ALL_SEPARATORS + event.getFileName().length()
            + event.getMessage().length() + severityLevelName.length()
            + getCheckShortName(event).length();
        return new StringBuilder(bufLen);
    }

    /**
     * Returns check name without 'Check' suffix.
     *
     * @param event audit event.
     * @return check name without 'Check' suffix.
     */
    private static String getCheckShortName(final AuditEvent event) {
        final String checkFullName = event.getSourceName();

        String checkShortName = checkFullName.substring(checkFullName.lastIndexOf('.') + 1);
        if (checkShortName.endsWith(SUFFIX)) {
            final int endIndex = checkShortName.length() - SUFFIX.length();
            checkShortName = checkShortName.substring(0, endIndex);
        }

        return checkShortName;
    }
}

