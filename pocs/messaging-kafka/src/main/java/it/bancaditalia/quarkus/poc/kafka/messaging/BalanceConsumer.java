package it.bancaditalia.quarkus.poc.kafka.messaging;

import io.smallrye.common.annotation.Blocking;
import it.bancaditalia.quarkus.poc.kafka.config.PaymentsConfig;
import it.bancaditalia.quarkus.poc.kafka.domain.AccountBalance;
import it.bancaditalia.quarkus.poc.kafka.domain.PaymentEvent;
import it.bancaditalia.quarkus.poc.kafka.domain.ProcessedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * The consumer: a local transaction per record, idempotent by event id (a rebalance can replay records that were
 * processed but not yet committed: throttled commits are in order, not immediate). A failing record is nacked:
 * the dead-letter-queue strategy publishes it to the dead-letter topic and the partition keeps flowing, which is
 * the Kafka difference from a queue: a poison message blocks nothing, and nothing redelivers it either.
 */
@ApplicationScoped
public class BalanceConsumer {

    private static final Logger LOG = Logger.getLogger(BalanceConsumer.class);

    @Inject
    EntityManager em;

    @Inject
    PaymentsConfig config;

    private final ConcurrentLinkedQueue<String> deadLetters = new ConcurrentLinkedQueue<>();

    @Incoming("payments-in")
    @Blocking
    @Transactional
    public void onPayment(PaymentEvent event) {
        if (em.find(ProcessedEvent.class, event.eventId()) != null) {
            LOG.infov("event {0} already applied, skipped", event.eventId());
            return;
        }
        if (event.account().startsWith(config.poisonPrefix())) {
            throw new IllegalStateException("cannot apply " + event.eventId() + " for " + event.account());
        }
        AccountBalance balance = Optional.ofNullable(em.find(AccountBalance.class, event.account(), LockModeType.PESSIMISTIC_WRITE))
                .orElseGet(() -> {
                    AccountBalance created = new AccountBalance(event.account());
                    em.persist(created);
                    return created;
                });
        balance.apply(event);
        em.persist(new ProcessedEvent(event.eventId()));
    }

    /** The operations view of the dead-letter topic: what failed, and why (the reason travels in a header). */
    @Incoming("payments-dlq")
    @Blocking
    public void onDeadLetter(PaymentEvent event) {
        deadLetters.add(event.eventId() + " for " + event.account());
    }

    public List<String> deadLetters() {
        return List.copyOf(deadLetters);
    }

    public Optional<AccountBalance> balance(String account) {
        return Optional.ofNullable(em.find(AccountBalance.class, account));
    }
}
