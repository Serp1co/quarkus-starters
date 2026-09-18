package it.bancaditalia.quarkus.poc.data.platform;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Plays the platform for the integration tests (prod profile): renders the contract keys the artifact needs. */
public class PlatformConfigResource implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        try {
            Path dir = Files.createDirectories(Path.of("target", "it-platform"));
            Files.writeString(dir.resolve("application.yaml"), """
                    registry:
                      supervisor: "Vigilanza (integration test)"
                      max-page-size: 5
                    """);
            return Map.of("quarkus.config.locations", dir.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void stop() {
    }
}
