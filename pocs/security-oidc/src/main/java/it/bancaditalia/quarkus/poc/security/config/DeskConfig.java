package it.bancaditalia.quarkus.poc.security.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import it.bancaditalia.quarkus.platform.contract.Doc;
import jakarta.validation.constraints.Min;

/** What the authorization desk needs from its environment. */
@ConfigMapping(prefix = "desk")
public interface DeskConfig {

    @Doc("Name of the unit that runs this desk, shown to the applicants; set per environment")
    String unit();

    @Min(1)
    @WithDefault("100")
    @Doc("Requests an operator may leave pending before new ones are refused")
    int maxPending();
}
