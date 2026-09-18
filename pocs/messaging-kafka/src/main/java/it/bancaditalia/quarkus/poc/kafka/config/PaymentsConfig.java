package it.bancaditalia.quarkus.poc.kafka.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import it.bancaditalia.quarkus.platform.contract.Doc;

@ConfigMapping(prefix = "payments")
public interface PaymentsConfig {

    @WithDefault("POISON")
    @Doc("POC hook: an event for an account starting with this prefix fails in the consumer and goes to the dead-letter topic")
    String poisonPrefix();
}
