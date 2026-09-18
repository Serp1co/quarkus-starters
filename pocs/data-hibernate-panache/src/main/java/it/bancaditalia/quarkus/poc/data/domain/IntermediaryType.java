package it.bancaditalia.quarkus.poc.data.domain;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Reference data: read on every request, changed a few times a year. {@code @Cacheable} puts it in the
 * second-level cache (Caffeine, local to the instance); the TTL is in application.yaml.
 */
@Entity
@Table(name = "intermediary_type")
@Cacheable
public class IntermediaryType {

    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String description;

    protected IntermediaryType() {
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
