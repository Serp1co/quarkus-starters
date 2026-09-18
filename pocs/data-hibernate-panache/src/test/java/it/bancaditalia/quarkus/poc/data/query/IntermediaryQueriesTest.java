package it.bancaditalia.quarkus.poc.data.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import it.bancaditalia.quarkus.poc.data.domain.Address;
import it.bancaditalia.quarkus.poc.data.domain.Branch;
import it.bancaditalia.quarkus.poc.data.domain.Intermediary;
import it.bancaditalia.quarkus.poc.data.legacy.IntermediaryDao;
import it.bancaditalia.quarkus.poc.data.panache.IntermediaryRepository;
import it.bancaditalia.quarkus.poc.data.service.RegistryService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * One test, two implementations: the EAP DAO and the Panache repository must answer the same. Every test runs
 * in a transaction that is rolled back (@TestTransaction), so the rows never reach the other tests; the rows the
 * REST tests committed are filtered out by the "prova" marker in the names, or measured as a baseline. The seed
 * is called from the test method, not from @BeforeEach: lifecycle callbacks get a transaction of their own,
 * rolled back before the test method starts.
 */
@QuarkusTest
@TestTransaction
class IntermediaryQueriesTest {

    private static final IntermediarySearch PROVA_BANKS = new IntermediarySearch("BANK", null, "prova", null);

    @Inject
    IntermediaryDao dao;

    @Inject
    IntermediaryRepository repository;

    @Inject
    RegistryService registry;

    @Inject
    EntityManager em;

    List<IntermediaryQueries> implementations;

    void seed() {
        implementations = List.of(dao, repository);
        registry.register("90001", "Prova Banca Nord", "BANK", new Address("Via Nazionale 91", "Roma", "RM", "00184"));
        registry.register("90002", "Prova Banca Sud", "BANK", new Address("Piazza Affari 1", "Milano", "MI", "20123"));
        registry.register("90003", "Prova SIM Torino", "SIM", new Address("Via Roma 1", "Torino", "TO", "10121"));
        registry.addBranch("90001", "001", new Address("Via Po 1", "Torino", "TO", "10124"));
        registry.addBranch("90001", "002", new Address("Via Etnea 1", "Catania", "CT", "95131"));
        registry.addBranch("90001", "003", new Address("Corso Vittorio 1", "Torino", "TO", "10125"));
        em.flush();
        em.clear(); // both implementations read from the database, not from the persistence context
    }

    @Test
    void findByAbi() {
        seed();
        for (IntermediaryQueries q : implementations) {
            assertEquals("Prova Banca Nord", q.findByAbi("90001").orElseThrow().getName(), q.getClass().getSimpleName());
            assertTrue(q.findByAbi("00000").isEmpty(), q.getClass().getSimpleName());
        }
    }

    @Test
    void searchWithFiltersSortingAndPaging() {
        seed();
        for (IntermediaryQueries q : implementations) {
            String impl = q.getClass().getSimpleName();
            PageResult<IntermediarySummary> banks = q.search(PROVA_BANKS, 0, 1, "name", true);
            assertEquals(2, banks.total(), impl);
            assertEquals(1, banks.items().size(), impl + ": one per page");
            assertEquals("Prova Banca Nord", banks.items().get(0).name(), impl + ": sorted by name");
            assertEquals("BANK", banks.items().get(0).typeCode(), impl + ": projection of type.code");

            PageResult<IntermediarySummary> secondPage = q.search(PROVA_BANKS, 1, 1, "name", true);
            assertEquals("Prova Banca Sud", secondPage.items().get(0).name(), impl + ": second page");

            PageResult<IntermediarySummary> byText = q.search(new IntermediarySearch(null, null, "PROVA", null), 0, 10, "abi", false);
            assertEquals(3, byText.total(), impl + ": case-insensitive contains");
            assertEquals("90003", byText.items().get(0).abi(), impl + ": descending by abi");

            PageResult<IntermediarySummary> inTurin = q.search(new IntermediarySearch(null, Intermediary.Status.ACTIVE, "prova", "TO"), 0, 10, "abi", true);
            assertEquals(1, inTurin.total(), impl + ": embedded attribute filter");
        }
    }

    @Test
    void countsBulkUpdatesAndNativeReports() {
        long activeBefore = dao.countByStatus(Intermediary.Status.ACTIVE);
        long suspendedBefore = dao.countByStatus(Intermediary.Status.SUSPENDED);
        long activeBanksBefore = dao.search(new IntermediarySearch("BANK", Intermediary.Status.ACTIVE, null, null), 0, 1, "abi", true).total();
        seed();
        for (IntermediaryQueries q : implementations) {
            String impl = q.getClass().getSimpleName();
            assertEquals(activeBefore + 3, q.countByStatus(Intermediary.Status.ACTIVE), impl);
            List<ProvinceCount> report = q.branchesPerProvince("90001");
            assertEquals(List.of(new ProvinceCount("CT", 1), new ProvinceCount("TO", 2)), report, impl + ": native report");
        }
        assertEquals(2, Branch.countInProvince("TO") - branchesInTurinBefore(), "active record count");
        // the bulk update once: the second implementation then sees what the first did
        assertEquals(activeBanksBefore + 2, dao.suspendAllOfType("BANK"), "every active bank suspended in one statement");
        em.clear();
        assertEquals(0, repository.suspendAllOfType("BANK"), "nothing left to suspend");
        assertEquals(suspendedBefore + activeBanksBefore + 2, repository.countByStatus(Intermediary.Status.SUSPENDED));
        assertEquals(activeBefore + 3 - activeBanksBefore - 2, dao.countByStatus(Intermediary.Status.ACTIVE));
    }

    private long branchesInTurinBefore() {
        return ((Number) em.createNativeQuery("select count(*) from branch where province = 'TO' and intermediary_id not in"
                + " (select id from intermediary where name like 'Prova%')").getSingleResult()).longValue();
    }
}
