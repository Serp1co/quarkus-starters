package it.bancaditalia.quarkus.poc.data.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterIntermediaryRequest(@NotBlank @Size(max = 5) String abi, @NotBlank @Size(max = 200) String name,
        @NotBlank String type, @NotNull @Valid AddressDto headquarters) {
}
