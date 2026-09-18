package it.bancaditalia.quarkus.config.messaging.kafka;

import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import it.bancaditalia.quarkus.platform.contract.ContractContributor;
import jakarta.enterprise.context.ApplicationScoped;

/** The cluster keys every Kafka application needs from the platform (design note 4.4). */
@ApplicationScoped
public class KafkaPlatformKeys implements ContractContributor {

    @Override
    public void contribute(ConfigContract.Builder builder) {
        builder.platform("kafka.bootstrap.servers", "String", true, "Kafka / AMQ Streams bootstrap servers")
                .platform("kafka.security.protocol", "String", "PLAINTEXT", "PLAINTEXT, SSL, SASL_SSL: the platform's choice per environment")
                .platform("kafka.sasl.mechanism", "String", "", "OAUTHBEARER with RHBK, SCRAM-SHA-512, or empty");
    }

    @Override
    public int order() {
        return PLATFORM_ORDER;
    }
}
