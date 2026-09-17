package it.bancaditalia.quarkus.poc.jakarta.domain;

import java.math.BigDecimal;

/**
 * Unchecked on purpose: a RuntimeException crossing a {@code @Transactional} boundary rolls the JTA
 * transaction back. On EAP the same effect needed {@code @ApplicationException(rollback = true)}.
 */
public class InsufficientFundsException extends RuntimeException {

    private final String iban;

    public InsufficientFundsException(String iban, BigDecimal balance, BigDecimal requested) {
        super("Account " + iban + " has " + balance + " available, " + requested + " requested");
        this.iban = iban;
    }

    public String getIban() {
        return iban;
    }
}
