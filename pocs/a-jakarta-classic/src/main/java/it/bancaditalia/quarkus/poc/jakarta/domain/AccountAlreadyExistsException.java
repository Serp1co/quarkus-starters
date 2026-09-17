package it.bancaditalia.quarkus.poc.jakarta.domain;

public class AccountAlreadyExistsException extends RuntimeException {

    public AccountAlreadyExistsException(String iban) {
        super("An account with IBAN " + iban + " already exists");
    }
}
