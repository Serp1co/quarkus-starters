package it.bancaditalia.quarkus.poc.data.api.dto;

import it.bancaditalia.quarkus.poc.data.domain.Branch;

public record BranchResponse(Long id, String code, AddressDto address) {

    public static BranchResponse of(Branch b) {
        return new BranchResponse(b.id, b.code, AddressDto.of(b.address));
    }
}
