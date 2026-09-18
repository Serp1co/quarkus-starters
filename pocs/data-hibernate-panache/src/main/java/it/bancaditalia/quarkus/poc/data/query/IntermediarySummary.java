package it.bancaditalia.quarkus.poc.data.query;

import io.quarkus.hibernate.orm.panache.common.ProjectedFieldName;
import it.bancaditalia.quarkus.poc.data.domain.Intermediary;

/**
 * A projection: the list view reads four columns, not the entity. The Criteria API builds it with
 * {@code cb.construct}, Panache with {@code project(IntermediarySummary.class)} ({@code @ProjectedFieldName} for
 * the one path that is not a direct attribute).
 */
public record IntermediarySummary(String abi, String name, Intermediary.Status status,
        @ProjectedFieldName("type.code") String typeCode) {
}
