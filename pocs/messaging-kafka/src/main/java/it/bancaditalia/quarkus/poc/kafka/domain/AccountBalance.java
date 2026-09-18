package it.bancaditalia.quarkus.poc.kafka.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** The consumer's projection: a running balance per account, plus the last sequence seen (ordering evidence). */
@Entity
@Table(name = "account_balance")
public class AccountBalance {

    @Id
    @Column(length = 34)
    private String account;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false)
    private int events;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "out_of_order", nullable = false)
    private int outOfOrder;

    protected AccountBalance() {
    }

    public AccountBalance(String account) {
        this.account = account;
        this.balance = BigDecimal.ZERO;
    }

    public void apply(PaymentEvent event) {
        balance = balance.add(event.amount());
        events++;
        if (event.sequence() < lastSequence) {
            outOfOrder++;
        }
        lastSequence = event.sequence();
    }

    public String getAccount() {
        return account;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public int getEvents() {
        return events;
    }

    public long getLastSequence() {
        return lastSequence;
    }

    public int getOutOfOrder() {
        return outOfOrder;
    }
}
