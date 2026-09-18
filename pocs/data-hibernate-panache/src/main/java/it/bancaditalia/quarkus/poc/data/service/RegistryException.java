package it.bancaditalia.quarkus.poc.data.service;

/** A business rule refused the request (422). */
public class RegistryException extends RuntimeException {

    public RegistryException(String message) {
        super(message);
    }
}
