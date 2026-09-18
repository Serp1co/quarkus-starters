package it.bancaditalia.quarkus.poc.remote.caller.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** A report on a counterparty. Status tells how far the remote step got: no transaction spans the two applications. */
public record Report(String id, String counterpartyCode, BigDecimal amount, String filedBy, Instant filedAt, Status status,
        String counterpartyName, String detail) {

    public enum Status {
        /** Filed locally; the counterparty was known to the registry. */
        COMPLETE,
        /** Filed locally; the registry did not know the counterparty and the registration is not confirmed yet. */
        PENDING_REGISTRY,
        /** Filed locally; the registry refused (the operator must look). */
        REJECTED_BY_REGISTRY
    }

    public Report with(Status newStatus, String name, String newDetail) {
        return new Report(id, counterpartyCode, amount, filedBy, filedAt, newStatus, name, newDetail);
    }
}
