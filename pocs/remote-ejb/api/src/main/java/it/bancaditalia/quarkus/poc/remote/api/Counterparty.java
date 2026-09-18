package it.bancaditalia.quarkus.poc.remote.api;

/** A counterparty of the registry, as the REST API returns it. */
public record Counterparty(String code, String name, String country, String status) {
}
