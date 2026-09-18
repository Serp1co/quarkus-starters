package it.bancaditalia.quarkus.poc.amqp.config;

import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import it.bancaditalia.quarkus.platform.contract.ContractContributor;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * The application's channels are the contract; the broker addresses behind them belong to the platform
 * (design note 4.3: "the developer only names the channel"). Defaults equal the channel's own name.
 */
@ApplicationScoped
public class OrdersContract implements ContractContributor {

    @Override
    public void contribute(ConfigContract.Builder builder) {
        builder.mapping(OrdersConfig.class)
                .platform("mp.messaging.outgoing.orders-out.address", "String", "orders", "AMQP address the outbox relay publishes order events to")
                .platform("mp.messaging.incoming.orders-in.address", "String", "orders", "AMQP address (queue) the order consumer reads; the same address for the POC's loop")
                .platform("orders.jms.notifications-queue", "String", "orders.notifications", "JMS queue of the notification variation");
    }
}
