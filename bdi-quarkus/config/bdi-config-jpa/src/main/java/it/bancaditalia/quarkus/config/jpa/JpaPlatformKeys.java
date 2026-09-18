package it.bancaditalia.quarkus.config.jpa;

import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import it.bancaditalia.quarkus.platform.contract.ContractContributor;
import jakarta.enterprise.context.ApplicationScoped;

/** The datasource keys every bdi-jpa-* application needs from the platform. */
@ApplicationScoped
public class JpaPlatformKeys implements ContractContributor {

    @Override
    public void contribute(ConfigContract.Builder builder) {
        builder.platform("quarkus.datasource.jdbc.url", "String", true, "JDBC URL of the application database (Agroal pool)")
                .platform("quarkus.datasource.username", "String", true, "Database user of this application")
                .platformSecret("quarkus.datasource.password", "Database password, delivered from the vault")
                .platform("quarkus.datasource.jdbc.max-size", "int", "20", "Upper bound of the connection pool")
                .platform("quarkus.transaction-manager.node-name", "String", "quarkus", "XA recovery node name: unique per instance (EAP: the server name)")
                .platform("quarkus.transaction-manager.object-store.directory", "String", "ObjectStore", "XA transaction log: persistent, node-local, outside the release directory (EAP: standalone/data/tx-object-store)");
    }

    @Override
    public int order() {
        return PLATFORM_ORDER;
    }
}
