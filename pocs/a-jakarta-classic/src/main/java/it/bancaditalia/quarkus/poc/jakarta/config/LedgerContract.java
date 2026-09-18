package it.bancaditalia.quarkus.poc.jakarta.config;

import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import it.bancaditalia.quarkus.platform.contract.ContractContributor;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * The application's half of the config contract: its {@code @ConfigMapping} interfaces. The platform half
 * (datasource, ports, log level) comes from the bdi-config-* modules of the starters in use, and
 * bdi-config-observability assembles the whole into {@code META-INF/config-contract.json}.
 */
@ApplicationScoped
public class LedgerContract implements ContractContributor {

    @Override
    public void contribute(ConfigContract.Builder builder) {
        builder.mapping(LedgerConfig.class);
    }
}
