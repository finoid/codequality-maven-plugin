package io.github.finoid.maven.plugins.codequality.storage;

import org.apache.maven.execution.MavenSession;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;

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
}
