package it.bancaditalia.quarkus.poc.data.api.dto;

import it.bancaditalia.quarkus.poc.data.domain.Intermediary;
import java.util.Map;

public record RegistryOverview(String supervisor, Map<Intermediary.Status, Long> byStatus) {
}
