package it.bancaditalia.quarkus.poc.kafka.messaging;

import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import it.bancaditalia.quarkus.poc.kafka.domain.PaymentEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

/** Produces keyed records: the account is the key, so all events of one account land on one partition, in order. */
@ApplicationScoped
public class PaymentProducer {

    @Inject
    @Channel("payments-out")
    Emitter<PaymentEvent> emitter;

    /** Completes when the broker has acknowledged the record (acks=all: every in-sync replica has it). */
    public CompletionStage<Void> publish(PaymentEvent event) {
        CompletableFuture<Void> acknowledged = new CompletableFuture<>();
        emitter.send(Message.of(event)
                .addMetadata(OutgoingKafkaRecordMetadata.<String> builder().withKey(event.account()).build())
                .withAck(() -> {
                    acknowledged.complete(null);
                    return CompletableFuture.completedFuture(null);
                })
                .withNack(reason -> {
                    acknowledged.completeExceptionally(reason);
                    return CompletableFuture.completedFuture(null);
                }));
        return acknowledged;
    }
}
