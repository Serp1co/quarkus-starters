package it.bancaditalia.quarkus.poc.jakarta.platform;

import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import it.bancaditalia.quarkus.poc.jakarta.config.LedgerConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * The config contract of this application (design note 2.2), defined once and used twice: exported to
 * {@code META-INF/config-contract.json} (kept in sync by ConfigContractTest, consumed by AAP before a deploy)
 * and resolved live by the conformance endpoint.
 * <p>
 * The platform half lists the Quarkus keys of the extensions in use. Once the bdi-quarkus-datasource and
 * bdi-quarkus-observability internal extensions exist, that half moves into them.
 */
@ApplicationScoped
public class PlatformContract {

    public static ConfigContract define() {
        return ConfigContract.builder()
                .mapping(LedgerConfig.class)
                .platform("quarkus.datasource.jdbc.url", "String", true, "JDBC URL of the ledger database (Agroal pool)")
                .platform("quarkus.datasource.username", "String", true, "Database user of this application")
                .platformSecret("quarkus.datasource.password", "Database password, delivered from the vault")
                .platform("quarkus.datasource.jdbc.max-size", "int", "50", "Upper bound of the connection pool")
                .platform("quarkus.http.port", "int", "8080", "Application port, the load balancer target")
                .platform("quarkus.management.port", "int", "9000", "Management port: health, conformance endpoint")
                .platform("quarkus.log.level", "String", "INFO", "Root log level")
                .build();
    }

    @Produces
    @Singleton
    ConfigContract contract() {
        return define();
    }
}
