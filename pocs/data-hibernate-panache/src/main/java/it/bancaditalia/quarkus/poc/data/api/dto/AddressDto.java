package it.bancaditalia.quarkus.poc.data.api.dto;

import it.bancaditalia.quarkus.poc.data.domain.Address;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressDto(@Size(max = 200) String street, @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(min = 2, max = 2) String province, @Size(max = 5) String postalCode) {

    public static AddressDto of(Address address) {
        return address == null ? null
                : new AddressDto(address.getStreet(), address.getCity(), address.getProvince(), address.getPostalCode());
    }

    public Address toDomain() {
        return new Address(street, city, province, postalCode);
    }
}
