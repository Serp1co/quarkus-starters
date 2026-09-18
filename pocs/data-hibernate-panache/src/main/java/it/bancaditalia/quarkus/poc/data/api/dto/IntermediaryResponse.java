package it.bancaditalia.quarkus.poc.data.api.dto;

import it.bancaditalia.quarkus.poc.data.domain.Intermediary;
import java.time.Instant;

public record IntermediaryResponse(Long id, String abi, String name, String type, Intermediary.Status status,
        AddressDto headquarters, int version, Instant registeredAt, Instant updatedAt) {

    public static IntermediaryResponse of(Intermediary i) {
        return new IntermediaryResponse(i.getId(), i.getAbi(), i.getName(), i.getType().getCode(), i.getStatus(),
                AddressDto.of(i.getHeadquarters()), i.getVersion(), i.getRegisteredAt(), i.getUpdatedAt());
    }
}
