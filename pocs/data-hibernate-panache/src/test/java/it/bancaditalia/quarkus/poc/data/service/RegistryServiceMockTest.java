package it.bancaditalia.quarkus.poc.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import it.bancaditalia.quarkus.poc.data.domain.Intermediary;
import it.bancaditalia.quarkus.poc.data.panache.IntermediaryRepository;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * The repository pattern is mockable: a service test without a database. The active-record entity (Branch)
 * needs PanacheMock instead, one reason the repository shape is the recommended one.
 */
@QuarkusTest
class RegistryServiceMockTest {

    @InjectMock
    IntermediaryRepository repository;

    @Inject
    RegistryService registry;

    @Test
    void overviewCountsEveryStatus() {
        when(repository.countByStatus(Intermediary.Status.ACTIVE)).thenReturn(120L);
        when(repository.countByStatus(Intermediary.Status.SUSPENDED)).thenReturn(3L);
        when(repository.countByStatus(Intermediary.Status.CANCELLED)).thenReturn(7L);
        var counts = registry.countByStatus();
        assertEquals(120L, counts.get(Intermediary.Status.ACTIVE));
        assertEquals(3L, counts.get(Intermediary.Status.SUSPENDED));
        assertEquals(7L, counts.get(Intermediary.Status.CANCELLED));
    }
}
