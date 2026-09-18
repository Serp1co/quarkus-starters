package it.bancaditalia.quarkus.poc.data.service;

public class NotRegisteredException extends RuntimeException {

    public NotRegisteredException(String abi) {
        super("No intermediary with ABI " + abi);
    }
}
