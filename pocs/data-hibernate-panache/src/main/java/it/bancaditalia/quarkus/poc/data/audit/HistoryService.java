package it.bancaditalia.quarkus.poc.data.audit;

import it.bancaditalia.quarkus.poc.data.domain.Intermediary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;

/**
 * The history of an intermediary, from the Envers audit tables: every revision with its timestamp and the
 * state of the row at that revision. On EAP this was either Envers as well, or a trigger and a shadow table.
 */
@ApplicationScoped
public class HistoryService {

    public record Revision(int revision, Instant at, RevisionType type, String name, Intermediary.Status status) {
    }

    @Inject
    EntityManager em;

    @Transactional
    @SuppressWarnings("unchecked")
    public List<Revision> of(String abi) {
        AuditReader reader = AuditReaderFactory.get(em);
        List<Object[]> rows = reader.createQuery()
                .forRevisionsOfEntity(Intermediary.class, false, true)
                .add(AuditEntity.property("abi").eq(abi))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();
        return rows.stream().map(row -> {
            Intermediary state = (Intermediary) row[0];
            DefaultRevisionEntity revision = (DefaultRevisionEntity) row[1];
            RevisionType type = (RevisionType) row[2];
            return new Revision(revision.getId(), Instant.ofEpochMilli(revision.getTimestamp()), type,
                    state.getName(), state.getStatus());
        }).toList();
    }
}
