package it.bancaditalia.quarkus.poc.data.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BranchRequest(@NotBlank @Size(max = 10) String code, @NotNull @Valid AddressDto address) {
}
