package it.bancaditalia.quarkus.poc.kafka.config;

import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import it.bancaditalia.quarkus.platform.contract.ContractContributor;
import jakarta.enterprise.context.ApplicationScoped;

/** Topics, consumer group and the dead-letter topic are platform keys (design note 4.4: topics and ACLs are AAP's). */
@ApplicationScoped
public class PaymentsContract implements ContractContributor {

    @Override
    public void contribute(ConfigContract.Builder builder) {
        builder.mapping(PaymentsConfig.class)
                .platform("mp.messaging.outgoing.payments-out.topic", "String", "payments", "Topic the API produces payment events to")
                .platform("mp.messaging.incoming.payments-in.topic", "String", "payments", "Topic the balance consumer reads")
                .platform("mp.messaging.incoming.payments-in.group.id", "String", "poc-messaging-kafka-balances", "Consumer group: the platform's naming convention")
                .platform("mp.messaging.incoming.payments-in.dead-letter-queue.topic", "String", "dead-letter-topic-payments-in", "Where failed records go")
                .platform("mp.messaging.incoming.payments-dlq.topic", "String", "dead-letter-topic-payments-in", "The dead-letter topic the operations consumer reads");
    }
}
