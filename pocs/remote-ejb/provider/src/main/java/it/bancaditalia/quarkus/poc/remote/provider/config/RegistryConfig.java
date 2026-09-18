package it.bancaditalia.quarkus.poc.remote.provider.config;

import io.smallrye.config.ConfigMapping;
import it.bancaditalia.quarkus.platform.contract.Doc;

@ConfigMapping(prefix = "registry")
public interface RegistryConfig {

    @Doc("Name of the unit that owns the registry, returned with every counterparty; set per environment")
    String unit();
}
