package it.bancaditalia.quarkus.poc.ejb.domain;

/**
 * An unexpected failure while settling a batch (a downstream system down, a constraint nobody expected).
 * Unchecked, so the batch transaction rolls back; the settlement run catches it and moves to the next batch.
 */
public class SettlementFailedException extends RuntimeException {

    public SettlementFailedException(String message) {
        super(message);
    }
}
