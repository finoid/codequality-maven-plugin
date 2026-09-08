package io.github.finoid.maven.plugins.codequality.storage;

import org.apache.maven.execution.MavenSession;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Supplier;

/**
 * Stores state which outlives a single mojo execution in the data context of the session.
 * <p>
 * The data context is shared by every module of the reactor - the per project session copies of a parallel build all
 * refer to the same repository session - and is safe for concurrent use.
 */
@Singleton
public class SessionRepository {
    private final MavenSession session;

    @Inject
    public SessionRepository(final MavenSession session) {
        this.session = session;
    }

    /**
     * Stores a value in the current session's data context under the given key.
     *
     * @param key   the key to associate with the value
     * @param value the value to store
     */
    public void put(final String key, final Object value) {
        session.getRepositorySession()
            .getData()
            .set(key, value);
    }

    /**
     * Retrieves a value associated with the given key from the session's data context.
     *
     * @param key the key to look up
     * @return the associated value, or {@code null} if no value is found
     */
    @Nullable
    public Object get(final String key) {
        return session.getRepositorySession()
            .getData()
            .get(key);
    }

    /**
     * Retrieves the value associated with the given key, storing and returning the value produced by the supplier if
     * no value is associated with it yet.
     * <p>
     * The supplier is invoked at most once per key, even when several builder threads race for it, which makes the
     * returned value safe to use as a shared, mutable holder of reactor wide state.
     *
     * @param key      the key to look up
     * @param supplier the supplier of the initial value
     * @return the already associated value, or the newly supplied one
     */
    public Object computeIfAbsent(final String key, final Supplier<Object> supplier) {
        return session.getRepositorySession()
            .getData()
            .computeIfAbsent(key, supplier);
    }
}
