package it.bancaditalia.quarkus.poc.amqp.repository;

import it.bancaditalia.quarkus.poc.amqp.domain.OutboxMessage;
import it.bancaditalia.quarkus.poc.amqp.domain.PaymentOrder;
import it.bancaditalia.quarkus.poc.amqp.domain.ProcessedMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class OrderRepository {

    @Inject
    EntityManager em;

    public void persist(Object entity) {
        em.persist(entity);
    }

    public Optional<PaymentOrder> findByReference(String reference) {
        return em.createNamedQuery(PaymentOrder.FIND_BY_REFERENCE, PaymentOrder.class)
                .setParameter("reference", reference).getResultList().stream().findFirst();
    }

    public Optional<PaymentOrder> lockByReference(String reference) {
        return findByReference(reference).map(o -> em.find(PaymentOrder.class, o.getId(), LockModeType.PESSIMISTIC_WRITE));
    }

    /** SKIP LOCKED: several relay instances never pick the same outbox row. */
    public List<OutboxMessage> lockPending(int max) {
        return em.createNamedQuery(OutboxMessage.PENDING, OutboxMessage.class)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", -2) // -2: SKIP LOCKED on PostgreSQL
                .setMaxResults(max)
                .getResultList();
    }

    public Optional<OutboxMessage> outbox(String id) {
        return Optional.ofNullable(em.find(OutboxMessage.class, id));
    }

    /** The outbox row of an order (POC hook for the resend). */
    public String lockPendingIdsFor(String reference) {
        return em.createQuery("select m.id from OutboxMessage m where m.aggregateId = :ref", String.class)
                .setParameter("ref", reference).getSingleResult();
    }

    public boolean alreadyProcessed(String messageId) {
        return em.find(ProcessedMessage.class, messageId) != null;
    }
}
