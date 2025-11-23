package io.github.finoid.maven.plugins.codequality.log;

import io.github.finoid.maven.plugins.codequality.util.Precondition;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.logging.Logger;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 * A logger implementation that logs messages to both a {@link Logger} and a file.
 * <p>
 * This class wraps an existing {@link Logger} instance and appends log messages to a specified file,
 * making it useful for cases where logs need to be both written to the standard logging system
 * and persisted to a file. This implementation is inspired by {@link org.apache.maven.monitor.logging.DefaultLog}.
 */
public class LogAndFileAppender implements Log {
    private final Logger logger;
    private final LogLevel logLevel;
    private final PrintStream printStream;

    @SuppressWarnings("required.method.not.called") // the FileOutputStream will be implicitly closed when the jvm exits
    public LogAndFileAppender(final Logger logger, final File file, final LogLevel logLevel) throws FileNotFoundException {
        this.logger = Precondition.nonNull(logger, "Logger shouldn't be null");
        this.logLevel = Precondition.nonNull(logLevel, "LogLevel shouldn't be null");
        this.printStream = new PrintStream(new FileOutputStream(Precondition.nonNull(file, "File shouldn't be null"), false));
    }

    @Override
    public void debug(final CharSequence content) {
        if (logLevel.compareTo(LogLevel.DEBUG) < 0) {
            return;
        }

        logger.debug(messageOfContent(content));
    }

    @Override
    public void debug(final CharSequence content, final Throwable error) {
        if (logLevel.compareTo(LogLevel.DEBUG) < 0) {
            return;
        }

        logger.debug(messageOfContent(content), error);
    }

    @Override
    public void debug(final Throwable error) {
        if (logLevel.compareTo(LogLevel.DEBUG) < 0) {
            return;
        }

        logger.debug("", error);
    }

    @Override
    public void info(final CharSequence content) {
        if (logLevel.compareTo(LogLevel.INFO) >= 0) {
            // logger.info(messageOfContent(content));
        }

        printStream.println(messageOfContent(content));
    }

    @Override
    public void info(final CharSequence content, final Throwable error) {
        if (logLevel.compareTo(LogLevel.INFO) >= 0) {
            // logger.info(messageOfContent(content), error);
        }

        printStream.println(messageOfContent(content));
    }

    @Override
    public void info(final Throwable error) {
        if (logLevel.compareTo(LogLevel.INFO) >= 0) {
            logger.info("", error);
        }

        printStream.println(error.toString());
    }

    @Override
    public void warn(final CharSequence content) {
        if (logLevel.compareTo(LogLevel.WARN) >= 0) {
            // logger.warn(messageOfContent(content));
        }

        printStream.println(messageOfContent(content));
    }

    @Override
    public void warn(final CharSequence content, final Throwable error) {
        if (logLevel.compareTo(LogLevel.WARN) >= 0) {
            logger.warn(messageOfContent(content), error);
        }

        printStream.println(messageOfContent(content));
    }

    @Override
    public void warn(final Throwable error) {
        if (logLevel.compareTo(LogLevel.WARN) >= 0) {
            logger.warn("", error);
        }

        printStream.println(error.toString());
    }

    @Override
    public void error(final CharSequence content) {
        logger.error(messageOfContent(content));

        printStream.println(messageOfContent(content));
    }

    @Override
    public void error(final CharSequence content, final Throwable error) {
        logger.error(messageOfContent(content), error);

        printStream.println(messageOfContent(content));
    }

    @Override
    public void error(final Throwable error) {
        logger.error("", error);

        printStream.println(error);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    private static String messageOfContent(@Nullable final CharSequence content) {
        if (content == null) {
            return "";
        }

        return content.toString();
    }
}
