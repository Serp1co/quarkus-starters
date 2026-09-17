package it.bancaditalia.quarkus.poc.jakarta.service;

import it.bancaditalia.quarkus.poc.jakarta.config.LedgerConfig;
import it.bancaditalia.quarkus.poc.jakarta.domain.Account;
import it.bancaditalia.quarkus.poc.jakarta.domain.AccountNotFoundException;
import it.bancaditalia.quarkus.poc.jakarta.domain.Transfer;
import it.bancaditalia.quarkus.poc.jakarta.domain.TransferRejectedException;
import it.bancaditalia.quarkus.poc.jakarta.repository.AccountRepository;
import it.bancaditalia.quarkus.poc.jakarta.repository.TransferRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Moves money between two accounts of this ledger in one JTA transaction. */
@ApplicationScoped
public class TransferService {

    @Inject
    AccountRepository accounts;

    @Inject
    TransferRepository transfers;

    @Inject
    LedgerConfig ledger;

    @Transactional
    public Transfer execute(String debtorIban, String creditorIban, BigDecimal amount, String reference) {
        LedgerConfig.TransferPolicy policy = ledger.transfer();
        if (debtorIban.equals(creditorIban)) {
            throw new TransferRejectedException("Debtor and creditor are the same account");
        }
        if (amount.compareTo(policy.maxAmount()) > 0) {
            throw new TransferRejectedException(
                    "Amount exceeds the maximum of " + policy.maxAmount() + " " + ledger.currency());
        }
        if (policy.requireReference() && (reference == null || reference.isBlank())) {
            throw new TransferRejectedException("A reference is required");
        }
        if (policy.blockedIbans().map(l -> l.contains(debtorIban) || l.contains(creditorIban)).orElse(false)) {
            throw new TransferRejectedException("One of the accounts is blocked");
        }

        Account debtor = accounts.findByIban(debtorIban).orElseThrow(() -> new AccountNotFoundException(debtorIban));
        Account creditor = accounts.findByIban(creditorIban)
                .orElseThrow(() -> new AccountNotFoundException(creditorIban));

        // Deliberately record and credit before debiting: when the debit fails, the transaction rollback
        // has to undo both the inserted row and the credit. TransferServiceTest proves that it does.
        Transfer transfer = new Transfer(debtor, creditor, amount, reference);
        transfers.persist(transfer);
        creditor.credit(amount);
        debtor.debit(amount);
        return transfer;
    }

    public Optional<Transfer> find(long id) {
        return transfers.findById(id);
    }

    public List<Transfer> forAccount(String iban) {
        return transfers.findByIban(iban);
    }
}
