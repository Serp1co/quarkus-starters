package it.bancaditalia.quarkus.poc.amqp.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import it.bancaditalia.quarkus.platform.contract.Doc;

@ConfigMapping(prefix = "orders")
public interface OrdersConfig {

    @WithDefault("POISON")
    @Doc("POC hook: an order whose reference starts with this prefix fails in the consumer, to show redelivery and the DLQ")
    String poisonPrefix();

    @WithDefault("50")
    @Doc("Outbox rows published per relay tick")
    int relayBatchSize();
}
