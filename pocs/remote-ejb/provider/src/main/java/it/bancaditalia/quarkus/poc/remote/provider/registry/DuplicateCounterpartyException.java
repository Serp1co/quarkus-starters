package it.bancaditalia.quarkus.poc.remote.provider.registry;

public class DuplicateCounterpartyException extends RuntimeException {

    public DuplicateCounterpartyException(String code) {
        super("Counterparty " + code + " is already registered");
    }
}
