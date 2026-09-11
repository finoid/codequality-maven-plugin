package io.github.finoid.maven.plugins.codequality.fixtures;

import org.apache.maven.plugin.logging.Log;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Log} which records everything written to it, together with the level it was written at.
 *
 * <p>Intended for asserting on console output: {@link #render()} returns the recorded log as a single
 * readable string, which makes it suitable for snapshotting.
 */
public class RecordingLog implements Log {
    private static final String ESCAPE = "\u001B";

    private final List<String> entries = new ArrayList<>();

    /**
     * The recorded entries, each prefixed with the level it was logged at.
     *
     * @return the recorded entries, in the order they were logged
     */
    public List<String> entries() {
        return List.copyOf(entries);
    }

    /**
     * Renders the recorded entries as a single string.
     *
     * <p>ANSI escapes are rendered as their literal, printable escape sequence, to keep the coloring of the
     * output both visible and diffable rather than being swallowed by whatever reads the snapshot.
     *
     * @return the recorded entries, separated by newlines
     */
    public String render() {
        return String.join("\n", entries)
            .replace(ESCAPE, "\\u001B");
    }

    private void record(final String level, @Nullable final CharSequence content) {
        entries.add("[" + level + "] " + content);
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public void debug(@Nullable final CharSequence content) {
        record("DEBUG", content);
    }

    @Override
    public void debug(@Nullable final CharSequence content, @Nullable final Throwable error) {
        record("DEBUG", content);
    }

    @Override
    public void debug(@Nullable final Throwable error) {
        record("DEBUG", String.valueOf(error));
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(@Nullable final CharSequence content) {
        record("INFO", content);
    }

    @Override
    public void info(@Nullable final CharSequence content, @Nullable final Throwable error) {
        record("INFO", content);
    }

    @Override
    public void info(@Nullable final Throwable error) {
        record("INFO", String.valueOf(error));
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(@Nullable final CharSequence content) {
        record("WARN", content);
    }

    @Override
    public void warn(@Nullable final CharSequence content, @Nullable final Throwable error) {
        record("WARN", content);
    }

    @Override
    public void warn(@Nullable final Throwable error) {
        record("WARN", String.valueOf(error));
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void error(@Nullable final CharSequence content) {
        record("ERROR", content);
    }

    @Override
    public void error(@Nullable final CharSequence content, @Nullable final Throwable error) {
        record("ERROR", content);
    }

    @Override
    public void error(@Nullable final Throwable error) {
        record("ERROR", String.valueOf(error));
    }
}
