package it.bancaditalia.quarkus.poc.jakarta.repository;

import it.bancaditalia.quarkus.poc.jakarta.domain.Account;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

/**
 * Plain JPA data access. On EAP this was a {@code @Stateless} bean with {@code @PersistenceContext};
 * Quarkus still honours {@code @PersistenceContext}, {@code @Inject} is simply the CDI-native spelling.
 */
@ApplicationScoped
public class AccountRepository {

    @Inject
    EntityManager em;

    public Optional<Account> findByIban(String iban) {
        return em.createNamedQuery(Account.FIND_BY_IBAN, Account.class)
                .setParameter("iban", iban)
                .getResultList()
                .stream()
                .findFirst();
    }

    public List<Account> findAll() {
        return em.createNamedQuery(Account.FIND_ALL, Account.class).getResultList();
    }

    public void persist(Account account) {
        em.persist(account);
    }
}
