package it.bancaditalia.quarkus.poc.data.legacy;

import it.bancaditalia.quarkus.poc.data.domain.Intermediary;
import it.bancaditalia.quarkus.poc.data.domain.IntermediaryType;
import it.bancaditalia.quarkus.poc.data.query.IntermediaryQueries;
import it.bancaditalia.quarkus.poc.data.query.IntermediarySearch;
import it.bancaditalia.quarkus.poc.data.query.IntermediarySummary;
import it.bancaditalia.quarkus.poc.data.query.PageResult;
import it.bancaditalia.quarkus.poc.data.query.ProvinceCount;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The DAO as it was on EAP ({@code @Stateless} + {@code @PersistenceContext}): now {@code @ApplicationScoped} +
 * {@code @Inject}, and nothing else changed. Named queries, the Criteria API for the dynamic search, JPQL bulk
 * update, native SQL. Keep it when it works; the point of the POC is that you do not have to rewrite it.
 */
@ApplicationScoped
public class IntermediaryDao implements IntermediaryQueries {

    @Inject
    EntityManager em;

    public void persist(Intermediary intermediary) {
        em.persist(intermediary);
    }

    @Override
    public Optional<Intermediary> findByAbi(String abi) {
        try {
            return Optional.of(em.createNamedQuery(Intermediary.BY_ABI, Intermediary.class)
                    .setParameter("abi", abi).getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public PageResult<IntermediarySummary> search(IntermediarySearch search, int page, int size, String sortField,
            boolean ascending) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<IntermediarySummary> cq = cb.createQuery(IntermediarySummary.class);
        Root<Intermediary> root = cq.from(Intermediary.class);
        Join<Intermediary, IntermediaryType> type = root.join("type");
        cq.select(cb.construct(IntermediarySummary.class,
                        root.get("abi"), root.get("name"), root.get("status"), type.get("code")))
                .where(predicates(cb, root, type, search).toArray(Predicate[]::new))
                .orderBy(ascending ? cb.asc(root.get(sortField)) : cb.desc(root.get(sortField)));
        List<IntermediarySummary> items = em.createQuery(cq)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Intermediary> countRoot = countQuery.from(Intermediary.class);
        Join<Intermediary, IntermediaryType> countType = countRoot.join("type");
        countQuery.select(cb.count(countRoot))
                .where(predicates(cb, countRoot, countType, search).toArray(Predicate[]::new));
        long total = em.createQuery(countQuery).getSingleResult();

        return new PageResult<>(items, total, page, size);
    }

    private static List<Predicate> predicates(CriteriaBuilder cb, Root<Intermediary> root,
            Join<Intermediary, IntermediaryType> type, IntermediarySearch search) {
        List<Predicate> predicates = new ArrayList<>();
        if (search.typeCode() != null) {
            predicates.add(cb.equal(type.get("code"), search.typeCode()));
        }
        if (search.status() != null) {
            predicates.add(cb.equal(root.get("status"), search.status()));
        }
        if (search.nameContains() != null) {
            predicates.add(cb.like(cb.lower(root.get("name")), "%" + search.nameContains().toLowerCase() + "%"));
        }
        if (search.province() != null) {
            Path<String> province = root.get("headquarters").get("province");
            predicates.add(cb.equal(province, search.province()));
        }
        return predicates;
    }

    @Override
    public long countByStatus(Intermediary.Status status) {
        return em.createNamedQuery(Intermediary.COUNT_BY_STATUS, Long.class)
                .setParameter("status", status).getSingleResult();
    }

    @Override
    public int suspendAllOfType(String typeCode) {
        return em.createQuery("update Intermediary i set i.status = :suspended, i.updatedAt = :now"
                        + " where i.type.code = :type and i.status = :active")
                .setParameter("suspended", Intermediary.Status.SUSPENDED)
                .setParameter("active", Intermediary.Status.ACTIVE)
                .setParameter("now", Instant.now())
                .setParameter("type", typeCode)
                .executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ProvinceCount> branchesPerProvince(String abi) {
        List<Object[]> rows = em.createNativeQuery("""
                select b.province, count(*) from branch b
                join intermediary i on i.id = b.intermediary_id
                where i.abi = :abi group by b.province order by b.province""")
                .setParameter("abi", abi)
                .getResultList();
        return rows.stream().map(r -> new ProvinceCount((String) r[0], ((Number) r[1]).longValue())).toList();
    }
}
