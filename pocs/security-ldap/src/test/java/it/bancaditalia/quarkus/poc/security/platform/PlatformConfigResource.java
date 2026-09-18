package it.bancaditalia.quarkus.poc.security.platform;

import it.bancaditalia.quarkus.test.ldap.AdLikeDirectory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Plays the platform for the integration tests (prod profile): starts the directory and renders what the
 * deploy role would render: the secret in secrets.yaml, the directory keys, the roles mapping of this
 * environment and the application keys in application.yaml, both named in QUARKUS_CONFIG_LOCATIONS.
 */
public class PlatformConfigResource extends AdLikeDirectory {

    @Override
    public Map<String, String> start() {
        Map<String, String> ldap = super.start();
        try {
            Path dir = Files.createDirectories(Path.of("target", "it-platform"));
            Files.writeString(dir.resolve("secrets.yaml"), """
                    quarkus:
                      security:
                        ldap:
                          dir-context:
                            password: "%s"
                    """.formatted(ldap.get("quarkus.security.ldap.dir-context.password")));
            Files.writeString(dir.resolve("application.yaml"), """
                    desk:
                      unit: "Servizio Autorizzazioni (integration test)"
                      max-pending: 3
                    quarkus:
                      security:
                        ldap:
                          dir-context:
                            url: "%s"
                            principal: "%s"
                          identity-mapping:
                            search-base-dn: "%s"
                            attribute-mappings:
                              groups:
                                filter-base-dn: "%s"
                      http:
                        auth:
                          roles-mapping:
                            "APP-ADMINS": admin,operator,reader
                            "APP-OPERATORS": operator,reader
                            "APP-READERS": reader
                    """.formatted(ldap.get("quarkus.security.ldap.dir-context.url"),
                    ldap.get("quarkus.security.ldap.dir-context.principal"),
                    ldap.get("quarkus.security.ldap.identity-mapping.search-base-dn"),
                    ldap.get("quarkus.security.ldap.identity-mapping.attribute-mappings.groups.filter-base-dn")));
            return Map.of("quarkus.config.locations",
                    dir.resolve("secrets.yaml").toAbsolutePath() + "," + dir.resolve("application.yaml").toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
