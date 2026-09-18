package it.bancaditalia.quarkus.platform.contract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lint for the developer-owned configuration file (design note 2.1): the artifact is built once and
 * deployed everywhere, so it must not carry environment-specific values. Only the inner-loop profiles
 * ({@code %dev}, {@code %test}) may appear; {@code %prod} or any environment name is banned.
 * Understands YAML ({@code "%prod":} sections) and properties ({@code %prod.key=}) alike.
 */
public final class ArtifactConfigLint {

    public static final Set<String> INNER_LOOP_PROFILES = Set.of("dev", "test");

    private ArtifactConfigLint() {
    }

    /** Offending lines, as {@code lineNumber: line}. */
    public static List<String> findBannedProfileKeys(Path configFile) throws IOException {
        List<String> offending = new ArrayList<>();
        List<String> lines = Files.readAllLines(configFile);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.startsWith("- ")) {
                line = line.substring(2).strip(); // a YAML list item that is itself a mapping
            }
            if (line.startsWith("\"") || line.startsWith("'")) {
                line = line.substring(1);
            }
            if (!line.startsWith("%")) {
                continue; // comments, blank lines and plain keys
            }
            int end = line.length();
            for (char terminator : new char[] { '.', '"', '\'', ':', '=' }) {
                int at = line.indexOf(terminator, 1);
                if (at > 0 && at < end) {
                    end = at;
                }
            }
            for (String profile : line.substring(1, end).split(",")) {
                if (!INNER_LOOP_PROFILES.contains(profile.strip())) {
                    offending.add((i + 1) + ": " + lines.get(i).strip());
                    break;
                }
            }
        }
        return offending;
    }

    public static void assertNoBannedProfileKeys(Path configFile) throws IOException {
        List<String> offending = findBannedProfileKeys(configFile);
        if (!offending.isEmpty()) {
            throw new AssertionError(configFile + " carries environment-specific profile keys. The artifact is "
                    + "built once and configured per environment by the platform (design note 2.1); move these "
                    + "keys to the platform inventory:\n  " + String.join("\n  ", offending));
        }
    }
}
