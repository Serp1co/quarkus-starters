package it.bancaditalia.quarkus.poc.data.panache;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import it.bancaditalia.quarkus.poc.data.domain.Intermediary;
import it.bancaditalia.quarkus.poc.data.query.IntermediaryQueries;
import it.bancaditalia.quarkus.poc.data.query.IntermediarySearch;
import it.bancaditalia.quarkus.poc.data.query.IntermediarySummary;
import it.bancaditalia.quarkus.poc.data.query.PageResult;
import it.bancaditalia.quarkus.poc.data.query.ProvinceCount;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The same operations as {@code IntermediaryDao}, with Panache: the repository pattern, on an entity that has
 * no idea Panache exists. Simplified queries ("abi", "status = :s"), named parameters in a map, {@code Sort},
 * {@code Page}, projections; the EntityManager is one call away for what the shorthand does not cover.
 * A normal CDI bean: services inject it, tests {@code @InjectMock} it.
 */
@ApplicationScoped
public class IntermediaryRepository implements PanacheRepositoryBase<Intermediary, Long>, IntermediaryQueries {

    @Override
    public Optional<Intermediary> findByAbi(String abi) {
        return find("abi", abi).firstResultOptional();
    }

    @Override
    public PageResult<IntermediarySummary> search(IntermediarySearch search, int page, int size, String sortField,
            boolean ascending) {
        StringBuilder query = new StringBuilder("1 = 1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (search.typeCode() != null) {
            query.append(" and type.code = :type");
            params.put("type", search.typeCode());
        }
        if (search.status() != null) {
            query.append(" and status = :status");
            params.put("status", search.status());
        }
        if (search.nameContains() != null) {
            query.append(" and lower(name) like :name");
            params.put("name", "%" + search.nameContains().toLowerCase() + "%");
        }
        if (search.province() != null) {
            query.append(" and headquarters.province = :province");
            params.put("province", search.province());
        }
        Sort sort = Sort.by(sortField, ascending ? Sort.Direction.Ascending : Sort.Direction.Descending);
        PanacheQuery<Intermediary> panacheQuery = find(query.toString(), sort, params);
        long total = panacheQuery.count();
        List<IntermediarySummary> items = panacheQuery.page(Page.of(page, size))
                .project(IntermediarySummary.class)
                .list();
        return new PageResult<>(items, total, page, size);
    }

    @Override
    public long countByStatus(Intermediary.Status status) {
        return count("status", status);
    }

    @Override
    public int suspendAllOfType(String typeCode) {
        return update("status = :suspended, updatedAt = :now where type.code = :type and status = :active",
                Map.of("suspended", Intermediary.Status.SUSPENDED, "active", Intermediary.Status.ACTIVE,
                        "now", Instant.now(), "type", typeCode));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ProvinceCount> branchesPerProvince(String abi) {
        List<Object[]> rows = getEntityManager().createNativeQuery("""
                select b.province, count(*) from branch b
                join intermediary i on i.id = b.intermediary_id
                where i.abi = :abi group by b.province order by b.province""")
                .setParameter("abi", abi)
                .getResultList();
        return rows.stream().map(r -> new ProvinceCount((String) r[0], ((Number) r[1]).longValue())).toList();
    }
}
