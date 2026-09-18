package it.bancaditalia.quarkus.poc.amqp.platform;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Plays the platform for the integration tests (prod profile): the addresses and the schema. */
public class PlatformConfigResource implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        try {
            Path dir = Files.createDirectories(Path.of("target", "it-platform"));
            Files.writeString(dir.resolve("application.yaml"), """
                    mp:
                      messaging:
                        incoming:
                          orders-in:
                            address: orders
                        outgoing:
                          orders-out:
                            address: orders
                    quarkus:
                      hibernate-orm:
                        schema-management:
                          strategy: drop-and-create
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
