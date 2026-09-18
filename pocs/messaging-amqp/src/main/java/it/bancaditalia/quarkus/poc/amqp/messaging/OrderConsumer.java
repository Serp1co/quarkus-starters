package it.bancaditalia.quarkus.poc.amqp.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.reactive.messaging.amqp.IncomingAmqpMetadata;
import it.bancaditalia.quarkus.poc.amqp.config.OrdersConfig;
import it.bancaditalia.quarkus.poc.amqp.domain.OrderEvent;
import it.bancaditalia.quarkus.poc.amqp.domain.PaymentOrder;
import it.bancaditalia.quarkus.poc.amqp.domain.ProcessedMessage;
import it.bancaditalia.quarkus.poc.amqp.repository.OrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

/**
 * The MDB's successor. On EAP: {@code @MessageDriven} with a container-managed XA transaction spanning the JMS
 * session and JPA; a rollback returned the message to the queue. Here: manual ack after a local JPA transaction,
 * nack on failure (the broker redelivers, then dead-letters after max-delivery-attempts), and an idempotent
 * consumer because delivery is at-least-once: a duplicate or a redelivery after a lost ack changes nothing.
 */
@ApplicationScoped
public class OrderConsumer {

    private static final Logger LOG = Logger.getLogger(OrderConsumer.class);

    @Inject
    OrderRepository orders;

    @Inject
    OrdersConfig config;

    @Inject
    ObjectMapper mapper;

    private final AtomicInteger deliveries = new AtomicInteger();
    private final AtomicInteger duplicates = new AtomicInteger();
    private final ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();

    @Incoming("orders-in")
    @Blocking
    public CompletionStage<Void> onOrder(Message<String> message) {
        String messageId = message.getMetadata(IncomingAmqpMetadata.class)
                .map(m -> String.valueOf(m.getId()))
                .orElse("no-id");
        deliveries.incrementAndGet();
        try {
            if (!process(messageId, message.getPayload())) {
                duplicates.incrementAndGet();
            }
            return message.ack();
        } catch (RuntimeException e) {
            failures.add(messageId + ": " + e.getMessage());
            LOG.warnv("order message {0} failed, nack for redelivery: {1}", messageId, e.getMessage());
            return message.nack(e);
        }
    }

    /** Idempotency and the business change in one local transaction; returns false for a duplicate. */
    @Transactional
    boolean process(String messageId, String payload) {
        if (orders.alreadyProcessed(messageId)) {
            LOG.infov("order message {0} already processed, skipped", messageId);
            return false;
        }
        OrderEvent event = parse(payload);
        PaymentOrder order = orders.lockByReference(event.reference())
                .orElseThrow(() -> new IllegalStateException("Unknown order " + event.reference()));
        order.delivered();
        if (event.reference().startsWith(config.poisonPrefix())) {
            throw new IllegalStateException("Downstream failure while processing " + event.reference());
        }
        order.processed();
        orders.persist(new ProcessedMessage(messageId));
        return true;
    }

    private OrderEvent parse(String payload) {
        try {
            return mapper.readValue(payload, OrderEvent.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int deliveries() {
        return deliveries.get();
    }

    public int duplicates() {
        return duplicates.get();
    }

    public List<String> failures() {
        return List.copyOf(failures);
    }
}
