package it.bancaditalia.quarkus.poc.amqp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The transactional outbox: the event is written in the same database transaction as the business change, and
 * published afterwards by the relay. This is what replaces "MDB + JPA in one XA transaction" (design note 4.3):
 * no XA across the database and the broker, at-least-once delivery, and an idempotent consumer on the other side.
 */
@Entity
@Table(name = "outbox")
@NamedQuery(name = OutboxMessage.PENDING, query = "select m from OutboxMessage m where m.sentAt is null order by m.createdAt")
public class OutboxMessage {

    public static final String PENDING = "OutboxMessage.pending";

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "aggregate_type", nullable = false, length = 40)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 35)
    private String aggregateId;

    @Column(nullable = false, length = 2000)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboxMessage() {
    }

    public OutboxMessage(String aggregateType, String aggregateId, String payload) {
        this.id = UUID.randomUUID().toString();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public void sent() {
        sentAt = Instant.now();
    }

    public void resend() {
        sentAt = null;
    }

    public String getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
