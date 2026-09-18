package it.bancaditalia.quarkus.poc.data.query;

import java.util.List;

public record PageResult<T>(List<T> items, long total, int page, int size) {
}
