package it.bancaditalia.quarkus.poc.ejb.domain;

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

/** A payment instruction waiting for the settlement batch. Plain JPA, as on EAP. */
@Entity
@Table(name = "instruction")
@NamedQuery(name = Instruction.PENDING_IDS, query = "select i.id from Instruction i where i.status = it.bancaditalia.quarkus.poc.ejb.domain.Instruction$Status.PENDING order by i.id")
@NamedQuery(name = Instruction.FIND_ALL, query = "select i from Instruction i order by i.id")
public class Instruction {

    public static final String PENDING_IDS = "Instruction.pendingIds";
    public static final String FIND_ALL = "Instruction.findAll";

    public enum Status {
        PENDING, SETTLED, REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 35)
    private String reference;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "run_id")
    private Long runId;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    protected Instruction() {
    }

    public Instruction(String reference, BigDecimal amount) {
        this.reference = reference;
        this.amount = amount;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public void settle(long runId) {
        this.status = Status.SETTLED;
        this.settledAt = Instant.now();
        this.runId = runId;
    }

    public void reject(long runId, String reason) {
        this.status = Status.REJECTED;
        this.settledAt = Instant.now();
        this.runId = runId;
        this.failureReason = reason;
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

    public Instant getSettledAt() {
        return settledAt;
    }

    public Long getRunId() {
        return runId;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
