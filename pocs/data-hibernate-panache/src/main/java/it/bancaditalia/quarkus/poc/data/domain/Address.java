package it.bancaditalia.quarkus.poc.data.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Value object shared by headquarters and branches: an @Embeddable, exactly as on EAP. */
@Embeddable
public class Address {

    @Column(length = 200)
    private String street;

    @Column(length = 100)
    private String city;

    @Column(length = 2)
    private String province;

    @Column(name = "postal_code", length = 5)
    private String postalCode;

    protected Address() {
    }

    public Address(String street, String city, String province, String postalCode) {
        this.street = street;
        this.city = city;
        this.province = province;
        this.postalCode = postalCode;
    }

    public String getStreet() {
        return street;
    }

    public String getCity() {
        return city;
    }

    public String getProvince() {
        return province;
    }

    public String getPostalCode() {
        return postalCode;
    }
}
