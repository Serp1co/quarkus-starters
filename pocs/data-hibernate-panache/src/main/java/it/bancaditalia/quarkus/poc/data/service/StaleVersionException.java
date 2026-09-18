package it.bancaditalia.quarkus.poc.data.service;

public class StaleVersionException extends RuntimeException {

    public StaleVersionException(String abi, int expected, int actual) {
        super("Intermediary " + abi + " changed since it was read (version " + expected + ", now " + actual + ")");
    }
}
