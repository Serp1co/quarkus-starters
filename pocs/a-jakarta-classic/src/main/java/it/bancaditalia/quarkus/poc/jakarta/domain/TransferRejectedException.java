package it.bancaditalia.quarkus.poc.jakarta.domain;

/** A business rule (from the ledger configuration) rejected the transfer before any balance moved. */
public class TransferRejectedException extends RuntimeException {

    public TransferRejectedException(String reason) {
        super(reason);
    }
}
