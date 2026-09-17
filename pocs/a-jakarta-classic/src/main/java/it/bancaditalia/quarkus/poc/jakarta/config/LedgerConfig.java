package it.bancaditalia.quarkus.poc.jakarta.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import it.bancaditalia.quarkus.platform.contract.Doc;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * What this application needs from its environment, and nothing else: the developer's half of the
 * config contract (design note 2.2). Keys without a default are rendered by the platform per environment;
 * a missing one fails the start-up, which is what the conformance check on every stage relies on.
 * <p>
 * On EAP the equivalent were JNDI lookups and system properties scattered through the code.
 */
@ConfigMapping(prefix = "ledger")
public interface LedgerConfig {

    @WithDefault("EUR")
    @Doc("ISO 4217 code of the currency every account of this ledger is denominated in")
    String currency();

    TransferPolicy transfer();

    interface TransferPolicy {

        @DecimalMin("0.01")
        @Doc("Upper bound for a single transfer; the platform sets it per environment")
        BigDecimal maxAmount();

        @WithDefault("false")
        @Doc("Reject transfers that carry no reference (causale)")
        boolean requireReference();

        @Doc("IBANs that may neither send nor receive, e.g. the sanctions list feed; comma separated")
        Optional<List<String>> blockedIbans();
    }
}
