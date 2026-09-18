package it.bancaditalia.quarkus.config.observability;

import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import it.bancaditalia.quarkus.platform.contract.ContractContributor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.Comparator;

/**
 * Assembles the application's config contract from every {@link ContractContributor} bean: the
 * application's own (its {@code @ConfigMapping} interfaces) first, then the platform keys declared by the
 * bdi-config-* modules in use. The result is what the conformance endpoint resolves and what
 * {@code META-INF/config-contract.json} must match.
 */
@ApplicationScoped
public class ContractAssembler {

    @Produces
    @Singleton
    ConfigContract assemble(Instance<ContractContributor> contributors) {
        ConfigContract.Builder builder = ConfigContract.builder();
        contributors.stream()
                .sorted(Comparator.comparingInt(ContractContributor::order)
                        .thenComparing(c -> c.getClass().getName()))
                .forEach(c -> c.contribute(builder));
        return builder.build();
    }
}
