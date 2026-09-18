package it.bancaditalia.quarkus.poc.remote.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterCounterparty(@NotBlank @Pattern(regexp = "[A-Z0-9]{4,11}") String code, @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(min = 2, max = 2) String country) {
}
