package it.bancaditalia.quarkus.poc.jakarta.platform;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Plays the platform for the integration tests. {@code @QuarkusIntegrationTest} runs the packaged artifact
 * with the prod profile, exactly as the systemd unit does, so the {@code %test} keys of
 * application.properties do not apply and the artifact refuses to start until the contract is rendered.
 * This resource renders it the platform way: a properties file at a fixed path, handed over through
 * {@code quarkus.config.locations} (the system-property twin of QUARKUS_CONFIG_LOCATIONS).
 */
public class PlatformConfigResource implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        try {
            Path dir = Files.createDirectories(Path.of("target", "it-platform"));
            Files.writeString(dir.resolve("application.yaml"), """
                    # Rendered for the integration tests; the AAP role does this for real environments.
                    ledger:
                      transfer:
                        max-amount: "1000.00"
                        blocked-ibans:
                          - IT66X0100503200000012345678
                    # The integration-test database is empty: let Hibernate create the schema (a DBA job elsewhere).
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
        // nothing to release
    }
}
