package it.bancaditalia.quarkus.poc.data.api.dto;

import it.bancaditalia.quarkus.poc.data.domain.IntermediaryType;

public record TypeResponse(String code, String description) {

    public static TypeResponse of(IntermediaryType t) {
        return new TypeResponse(t.getCode(), t.getDescription());
    }
}
