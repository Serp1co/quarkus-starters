package it.bancaditalia.quarkus.poc.data.query;

import it.bancaditalia.quarkus.poc.data.domain.Intermediary;

/** Dynamic search criteria: every field optional, combined with AND. */
public record IntermediarySearch(String typeCode, Intermediary.Status status, String nameContains, String province) {

    public static IntermediarySearch none() {
        return new IntermediarySearch(null, null, null, null);
    }
}
