package it.beta;

import it.alpha.Alpha;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Beta {
    // Checker Framework: required.method.not.called - the stream is never closed
    public int sizeOfBoth(final Path path) throws IOException {
        final InputStream stream = Files.newInputStream(path);

        return stream.available() + new Alpha().size(path);
    }
}
