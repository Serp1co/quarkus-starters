package it.bancaditalia.quarkus.poc.jakarta.service;

import it.bancaditalia.quarkus.poc.jakarta.domain.Account;
import it.bancaditalia.quarkus.poc.jakarta.domain.AccountAlreadyExistsException;
import it.bancaditalia.quarkus.poc.jakarta.domain.AccountNotFoundException;
import it.bancaditalia.quarkus.poc.jakarta.repository.AccountRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;

/**
 * Was a {@code @Stateless} session bean on EAP. Quarkus has no EJB container: the bean becomes a CDI
 * {@code @ApplicationScoped} bean and the container-managed transaction becomes {@code @Transactional}
 * (Narayana), with the same REQUIRED semantics.
 */
@ApplicationScoped
public class AccountService {

    @Inject
    AccountRepository accounts;

    @Transactional
    public Account open(String iban, String holder, BigDecimal initialBalance) {
        if (accounts.findByIban(iban).isPresent()) {
            throw new AccountAlreadyExistsException(iban);
        }
        Account account = new Account(iban, holder, initialBalance);
        accounts.persist(account);
        return account;
    }

    public Account get(String iban) {
        return accounts.findByIban(iban).orElseThrow(() -> new AccountNotFoundException(iban));
    }

    public List<Account> list() {
        return accounts.findAll();
    }
}
