package it.bancaditalia.quarkus.poc.security.platform;

import it.bancaditalia.quarkus.test.oidc.RhbkLikeRealm;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Plays the platform for the integration tests (prod profile): starts the realm and renders what the deploy
 * role would render: the client secret in secrets.yaml; the realm URL, the client, the roles mapping of this
 * environment and the application keys in application.yaml, both named in QUARKUS_CONFIG_LOCATIONS.
 */
public class PlatformConfigResource extends RhbkLikeRealm {

    @Override
    public Map<String, String> start() {
        Map<String, String> oidc = super.start();
        try {
            Path dir = Files.createDirectories(Path.of("target", "it-platform"));
            Files.writeString(dir.resolve("secrets.yaml"), """
                    quarkus:
                      oidc:
                        credentials:
                          secret: "%s"
                    """.formatted(oidc.get("quarkus.oidc.credentials.secret")));
            Files.writeString(dir.resolve("application.yaml"), """
                    desk:
                      unit: "Servizio Autorizzazioni (integration test)"
                      max-pending: 3
                    quarkus:
                      oidc:
                        auth-server-url: "%s"
                        client-id: "%s"
                        tls:
                          verification: "%s"   # none: the look-alike realm speaks plain HTTP; never rendered for an environment
                      http:
                        auth:
                          roles-mapping:
                            "app-admins": admin,operator,reader
                            "app-operators": operator,reader
                            "app-readers": reader
                    """.formatted(oidc.get("quarkus.oidc.auth-server-url"), oidc.get("quarkus.oidc.client-id"),
                    oidc.get("quarkus.oidc.tls.verification")));
            return Map.of("quarkus.config.locations",
                    dir.resolve("secrets.yaml").toAbsolutePath() + "," + dir.resolve("application.yaml").toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
