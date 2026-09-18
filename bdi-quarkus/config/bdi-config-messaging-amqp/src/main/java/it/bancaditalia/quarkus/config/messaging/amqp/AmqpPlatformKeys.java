package it.bancaditalia.quarkus.config.messaging.amqp;

import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import it.bancaditalia.quarkus.platform.contract.ContractContributor;
import jakarta.enterprise.context.ApplicationScoped;

/** The broker keys every AMQP application needs from the platform (design note 4.3: the topology is AAP's). */
@ApplicationScoped
public class AmqpPlatformKeys implements ContractContributor {

    @Override
    public void contribute(ConfigContract.Builder builder) {
        builder.platform("amqp-host", "String", true, "AMQ Broker host (AMQP 1.0 acceptor)")
                .platform("amqp-port", "int", "5672", "AMQ Broker AMQP port")
                .platform("amqp-username", "String", true, "Broker user of this application")
                .platformSecret("amqp-password", "Broker password, delivered from the vault")
                .platform("amqp-use-ssl", "boolean", "false", "TLS to the broker (with the TLS registry, cookbook 12)");
    }

    @Override
    public int order() {
        return PLATFORM_ORDER;
    }
}
