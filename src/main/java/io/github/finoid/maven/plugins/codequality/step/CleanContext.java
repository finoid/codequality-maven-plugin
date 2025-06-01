package io.github.finoid.maven.plugins.codequality.step;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import io.github.finoid.maven.plugins.codequality.util.Precondition;

import java.util.Optional;

@Value
public class CleanContext {
    public static CleanContext DO_NOTHING = new CleanContext(CleanType.NOTHING, null, null);

    CleanType type;

    /**
     * The directory which includes the files to be deleted.
     */
    @Getter(AccessLevel.NONE)
    @Nullable
    String fileDirectory;

    /**
     * The file selector.
     * <p>
     * Supports wildcards.
     */
    @Getter(AccessLevel.NONE)
    @Nullable
    String fileSelector;

    /**
     * The CleanContext constructor.
     *
     * @param type          the clean type
     * @param fileDirectory the file directory. Shouldn't be null for {@link CleanType#DIRECTORY}.
     * @param fileSelector  the file selector. Shouldn't be null for {@link CleanType#DIRECTORY}.
     * @throws IllegalArgumentException in case of an illegal argument.
     */
    public CleanContext(final CleanType type, final @Nullable String fileDirectory, final @Nullable String fileSelector) {
        this.type = type;
        this.fileDirectory = fileDirectory;
        this.fileSelector = fileSelector;

        validateSelf();
    }

    public Optional<String> optionalFileDirectory() {
        return Optional.ofNullable(fileDirectory);
    }

    public Optional<String> optionalFileSelector() {
        return Optional.ofNullable(fileSelector);
    }

    private void validateSelf() {
        if (type == CleanType.DIRECTORY) {
            Precondition.nonBlank(fileDirectory, "File directory shouldn't be blank for type CLEAN_DIRECTORY");
            Precondition.nonBlank(fileSelector, "File selector shouldn't be blank for type CLEAN_DIRECTORY");
        }
    }

    public enum CleanType {
        /**
         * Cleans nothing.
         */
        NOTHING,
        /**
         * Cleans parts of the target directory.
         */
        DIRECTORY,
        /**
         * Cleans the entire target directory.
         */
        ALL
    }
}