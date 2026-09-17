package it.bancaditalia.quarkus.platform.contract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lint for the developer-owned {@code application.properties} (design note 2.1): the artifact is built
 * once and deployed everywhere, so it must not carry environment-specific values. Only the inner-loop
 * profiles ({@code %dev}, {@code %test}) may appear; {@code %prod} or any environment name is banned.
 */
public final class ArtifactConfigLint {

    public static final Set<String> INNER_LOOP_PROFILES = Set.of("dev", "test");

    private ArtifactConfigLint() {
    }

    /** Offending lines, as {@code lineNumber: line}. */
    public static List<String> findBannedProfileKeys(Path propertiesFile) throws IOException {
        List<String> offending = new ArrayList<>();
        List<String> lines = Files.readAllLines(propertiesFile);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (!line.startsWith("%")) {
                continue; // comments, blank lines and plain keys
            }
            int dot = line.indexOf('.');
            if (dot < 0) {
                continue;
            }
            for (String profile : line.substring(1, dot).split(",")) {
                if (!INNER_LOOP_PROFILES.contains(profile.strip())) {
                    offending.add((i + 1) + ": " + line);
                    break;
                }
            }
        }
        return offending;
    }

    public static void assertNoBannedProfileKeys(Path propertiesFile) throws IOException {
        List<String> offending = findBannedProfileKeys(propertiesFile);
        if (!offending.isEmpty()) {
            throw new AssertionError(propertiesFile + " carries environment-specific profile keys. The artifact is "
                    + "built once and configured per environment by the platform (design note 2.1); move these "
                    + "keys to the platform inventory:\n  " + String.join("\n  ", offending));
        }
    }
}
