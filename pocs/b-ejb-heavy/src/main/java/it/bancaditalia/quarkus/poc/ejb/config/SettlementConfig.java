package it.bancaditalia.quarkus.poc.ejb.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import it.bancaditalia.quarkus.platform.contract.Doc;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

/** What the settlement needs from its environment: on EAP, timer intervals and limits were system properties. */
@ConfigMapping(prefix = "settlement")
public interface SettlementConfig {

    @WithDefault("10s")
    @Doc("Interval of the settlement timer (Quartz, one execution per interval across the cluster)")
    String interval();

    @Min(1)
    @WithDefault("50")
    @Doc("Instructions settled per transaction; a failing batch rolls back alone")
    int batchSize();

    @DecimalMin("0.01")
    @Doc("Instructions above this amount are rejected, not settled; the platform sets it per environment")
    BigDecimal maxInstructionAmount();

    @WithDefault("ERR")
    @Doc("POC hook: an instruction whose reference starts with this prefix fails unexpectedly, to show batch isolation")
    String failurePrefix();
}
