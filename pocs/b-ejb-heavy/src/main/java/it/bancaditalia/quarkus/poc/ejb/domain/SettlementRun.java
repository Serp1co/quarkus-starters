package it.bancaditalia.quarkus.poc.ejb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import java.time.Instant;

/** One execution of the settlement timer: which node ran it, and what it did. */
@Entity
@Table(name = "settlement_run")
@NamedQuery(name = SettlementRun.FIND_ALL, query = "select r from SettlementRun r order by r.id desc")
public class SettlementRun {

    public static final String FIND_ALL = "SettlementRun.findAll";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String node;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false)
    private int settled;

    @Column(nullable = false)
    private int rejected;

    /** Batches whose transaction rolled back: their instructions are still pending for the next run. */
    @Column(nullable = false)
    private int failed;

    protected SettlementRun() {
    }

    public SettlementRun(String node) {
        this.node = node;
        this.startedAt = Instant.now();
    }

    public void record(int settled, int rejected) {
        this.settled += settled;
        this.rejected += rejected;
    }

    public void batchFailed() {
        this.failed++;
    }

    public void finish() {
        this.finishedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getNode() {
        return node;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getSettled() {
        return settled;
    }

    public int getRejected() {
        return rejected;
    }

    public int getFailed() {
        return failed;
    }
}
