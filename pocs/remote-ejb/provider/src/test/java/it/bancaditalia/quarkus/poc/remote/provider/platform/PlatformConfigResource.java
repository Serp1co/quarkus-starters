package it.bancaditalia.quarkus.poc.remote.provider.platform;

import it.bancaditalia.quarkus.test.oidc.RhbkLikeRealm;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Plays the platform for the integration tests (prod profile): the realm, and the rendered files. */
public class PlatformConfigResource extends RhbkLikeRealm {

    @Override
    public Map<String, String> start() {
        Map<String, String> oidc = super.start();
        try {
            Path dir = Files.createDirectories(Path.of("target", "it-platform"));
            Files.writeString(dir.resolve("secrets.yaml"), "quarkus:\n  oidc:\n    credentials:\n      secret: \"%s\"\n".formatted(oidc.get("quarkus.oidc.credentials.secret")));
            Files.writeString(dir.resolve("application.yaml"), """
                    registry:
                      unit: "Anagrafe Controparti (integration test)"
                    quarkus:
                      oidc:
                        auth-server-url: "%s"
                        client-id: "%s"
                        tls:
                          verification: none
                      http:
                        auth:
                          roles-mapping:
                            "app-readers": registry-reader
                            "app-services": registry-reader,registry-writer
                      grpc:
                        clients:
                          counterparty:
                            host: localhost
                            port: 8081
                            plain-text: true
                    """.formatted(oidc.get("quarkus.oidc.auth-server-url"), oidc.get("quarkus.oidc.client-id")));
            return Map.of("quarkus.config.locations",
                    dir.resolve("secrets.yaml").toAbsolutePath() + "," + dir.resolve("application.yaml").toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
