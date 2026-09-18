package it.bancaditalia.quarkus.poc.amqp.messaging;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.reactive.messaging.amqp.OutgoingAmqpMetadata;
import io.smallrye.reactive.messaging.annotations.Broadcast;
import it.bancaditalia.quarkus.poc.amqp.config.OrdersConfig;
import it.bancaditalia.quarkus.poc.amqp.domain.OutboxMessage;
import it.bancaditalia.quarkus.poc.amqp.repository.OrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

/**
 * Publishes the outbox to the broker. Runs on every instance (in-memory scheduler); SKIP LOCKED keeps two relays
 * from publishing the same row. A row is marked sent only when the broker has accepted the message, so a broker
 * outage means a later retry, never a lost event: at-least-once, which is why the consumer is idempotent.
 */
@ApplicationScoped
public class OutboxRelay {

    private static final Logger LOG = Logger.getLogger(OutboxRelay.class);

    @Inject
    OrderRepository orders;

    @Inject
    OrdersConfig config;

    @Inject
    @Channel("orders-out")
    Emitter<String> emitter;

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void relay() {
        int published = publishPending();
        if (published > 0) {
            LOG.infov("outbox relay: {0} events published", published);
        }
    }

    @Transactional
    public int publishPending() {
        List<OutboxMessage> pending = orders.lockPending(config.relayBatchSize());
        int published = 0;
        for (OutboxMessage message : pending) {
            CompletableFuture<Void> accepted = new CompletableFuture<>();
            emitter.send(Message.of(message.getPayload())
                    .addMetadata(OutgoingAmqpMetadata.builder()
                            .withMessageId(message.getId())
                            .withContentType("application/json")
                            .withDurable(true)
                            .build())
                    .withAck(() -> {
                        accepted.complete(null);
                        return CompletableFuture.completedFuture(null);
                    })
                    .withNack(reason -> {
                        accepted.completeExceptionally(reason);
                        return CompletableFuture.completedFuture(null);
                    }));
            try {
                accepted.join(); // wait for the broker's settlement before marking the row
                message.sent();
                published++;
            } catch (RuntimeException e) {
                LOG.warnv("outbox {0} not accepted by the broker, will retry: {1}", message.getId(), e.getMessage());
                break;
            }
        }
        return published;
    }

    /** POC hook: put an already sent row back into the outbox, to show the idempotent consumer at work. */
    @Transactional
    public boolean resend(String outboxId) {
        return orders.outbox(outboxId).map(m -> {
            m.resend();
            return true;
        }).orElse(false);
    }

    public CompletionStage<Void> nothing() {
        return CompletableFuture.completedFuture(null);
    }
}
