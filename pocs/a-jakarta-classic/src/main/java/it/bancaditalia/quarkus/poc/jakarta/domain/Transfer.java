package it.bancaditalia.quarkus.poc.jakarta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** A settled transfer between two accounts of this ledger. */
@Entity
@Table(name = "transfer")
@NamedQuery(name = Transfer.FIND_BY_IBAN, query = """
        select t from Transfer t join fetch t.debtor join fetch t.creditor
        where t.debtor.iban = :iban or t.creditor.iban = :iban
        order by t.executedAt desc, t.id desc""")
public class Transfer {

    public static final String FIND_BY_IBAN = "Transfer.findByIban";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "debtor_id", nullable = false)
    private Account debtor;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "creditor_id", nullable = false)
    private Account creditor;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 140)
    private String reference;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    protected Transfer() {
        // JPA
    }

    public Transfer(Account debtor, Account creditor, BigDecimal amount, String reference) {
        this.debtor = debtor;
        this.creditor = creditor;
        this.amount = amount;
        this.reference = reference;
        this.executedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Account getDebtor() {
        return debtor;
    }

    public Account getCreditor() {
        return creditor;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReference() {
        return reference;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
