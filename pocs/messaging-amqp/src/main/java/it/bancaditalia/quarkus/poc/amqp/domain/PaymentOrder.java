package it.bancaditalia.quarkus.poc.amqp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** A payment order: written by the REST side, processed by the consumer that replaced the MDB. */
@Entity
@Table(name = "payment_order")
@NamedQuery(name = PaymentOrder.FIND_BY_REFERENCE, query = "select o from PaymentOrder o where o.reference = :reference")
public class PaymentOrder {

    public static final String FIND_BY_REFERENCE = "PaymentOrder.findByReference";

    public enum Status {
        NEW, PROCESSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 35, unique = true)
    private String reference;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    /** How many times the consumer has seen a message for this order, duplicates included. */
    @Column(name = "deliveries", nullable = false)
    private int deliveries;

    protected PaymentOrder() {
    }

    public PaymentOrder(String reference, BigDecimal amount) {
        this.reference = reference;
        this.amount = amount;
        this.status = Status.NEW;
        this.createdAt = Instant.now();
    }

    public void delivered() {
        deliveries++;
    }

    public void processed() {
        status = Status.PROCESSED;
        processedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public int getDeliveries() {
        return deliveries;
    }
}
