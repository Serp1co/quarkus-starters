package it.bancaditalia.quarkus.poc.jakarta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A current account. Plain JPA entity, unchanged from what it was on EAP: no Panache, no Quarkus import.
 */
@Entity
@Table(name = "account", uniqueConstraints = @UniqueConstraint(name = "uk_account_iban", columnNames = "iban"))
@NamedQuery(name = Account.FIND_BY_IBAN, query = "select a from Account a where a.iban = :iban")
@NamedQuery(name = Account.FIND_ALL, query = "select a from Account a order by a.iban")
public class Account {

    public static final String FIND_BY_IBAN = "Account.findByIban";
    public static final String FIND_ALL = "Account.findAll";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 34)
    private String iban;

    @Column(nullable = false, length = 120)
    private String holder;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Optimistic locking: two concurrent transfers on the same account cannot both commit. */
    @Version
    private long version;

    protected Account() {
        // JPA
    }

    public Account(String iban, String holder, BigDecimal initialBalance) {
        this.iban = iban;
        this.holder = holder;
        this.balance = initialBalance;
        this.createdAt = Instant.now();
    }

    /** Throws (unchecked) when the balance is insufficient, which makes the enclosing JTA transaction roll back. */
    public void debit(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(iban, balance, amount);
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public Long getId() {
        return id;
    }

    public String getIban() {
        return iban;
    }

    public String getHolder() {
        return holder;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
