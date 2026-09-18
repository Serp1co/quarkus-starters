package it.bancaditalia.quarkus.poc.data.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import it.bancaditalia.quarkus.platform.contract.Doc;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** What the register needs from its environment. The contract is derived from this interface at build time. */
@ConfigMapping(prefix = "registry")
public interface RegistryConfig {

    @Doc("Name of the supervising unit that owns this register, shown in the overview; set per environment")
    String supervisor();

    @WithDefault("\\d{5}")
    @Doc("Regular expression an ABI code must match to be registered")
    String abiPattern();

    @Min(1)
    @Max(1000)
    @WithDefault("100")
    @Doc("Largest page a client may ask for on the search endpoint")
    int maxPageSize();
}
