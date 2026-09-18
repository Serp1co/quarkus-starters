package it.bancaditalia.quarkus.poc.data.api.dto;

import it.bancaditalia.quarkus.poc.data.domain.Intermediary;
import jakarta.validation.constraints.NotNull;

/** The version is the one the client read: a stale version is refused with 409. */
public record StatusChangeRequest(@NotNull Intermediary.Status status, @NotNull Integer version) {
}
