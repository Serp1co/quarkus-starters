package it.bancaditalia.quarkus.poc.data.query;

import it.bancaditalia.quarkus.poc.data.domain.Intermediary;
import java.util.List;
import java.util.Optional;

/**
 * The data-access operations the register needs, implemented twice: {@code legacy.IntermediaryDao} with the
 * EntityManager as written for EAP, {@code panache.IntermediaryRepository} with Panache. One test runs both.
 */
public interface IntermediaryQueries {

    Optional<Intermediary> findByAbi(String abi);

    /** Dynamic filters, sorting and paging: on EAP the Criteria API, with Panache a query string and Parameters. */
    PageResult<IntermediarySummary> search(IntermediarySearch search, int page, int size, String sortField, boolean ascending);

    long countByStatus(Intermediary.Status status);

    /** Bulk update: one statement, no entities loaded. Returns the rows touched. */
    int suspendAllOfType(String typeCode);

    /** A native report: SQL is not hidden by either API. */
    List<ProvinceCount> branchesPerProvince(String abi);
}
