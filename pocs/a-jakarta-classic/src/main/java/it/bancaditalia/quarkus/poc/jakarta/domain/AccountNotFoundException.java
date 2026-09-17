package it.bancaditalia.quarkus.poc.jakarta.domain;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String iban) {
        super("No account with IBAN " + iban);
    }
}
