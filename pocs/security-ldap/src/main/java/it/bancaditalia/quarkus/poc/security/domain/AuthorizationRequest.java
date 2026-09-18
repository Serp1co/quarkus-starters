package it.bancaditalia.quarkus.poc.security.domain;

import java.time.Instant;

/** A request for an authorization, as filed by an operator and decided by an admin. In memory: the POC is about who may do what. */
public record AuthorizationRequest(long id, String applicant, String subject, String filedBy, Instant filedAt,
        Status status, String decidedBy, Instant decidedAt, String note) {

    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    public AuthorizationRequest decide(Status decision, String by, String note) {
        return new AuthorizationRequest(id, applicant, subject, filedBy, filedAt, decision, by, Instant.now(), note);
    }
}
