package it.alpha;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Alpha {
    // Checker Framework: required.method.not.called - the stream is never closed
    public int size(final Path path) throws IOException {
        final InputStream stream = Files.newInputStream(path);

        return stream.readAllBytes().length;
    }
}
