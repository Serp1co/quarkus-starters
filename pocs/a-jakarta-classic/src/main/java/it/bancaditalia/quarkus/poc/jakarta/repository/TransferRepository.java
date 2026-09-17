package it.bancaditalia.quarkus.poc.jakarta.repository;

import it.bancaditalia.quarkus.poc.jakarta.domain.Transfer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TransferRepository {

    @Inject
    EntityManager em;

    public void persist(Transfer transfer) {
        em.persist(transfer);
    }

    public Optional<Transfer> findById(long id) {
        return Optional.ofNullable(em.find(Transfer.class, id));
    }

    public List<Transfer> findByIban(String iban) {
        return em.createNamedQuery(Transfer.FIND_BY_IBAN, Transfer.class)
                .setParameter("iban", iban)
                .getResultList();
    }
}
