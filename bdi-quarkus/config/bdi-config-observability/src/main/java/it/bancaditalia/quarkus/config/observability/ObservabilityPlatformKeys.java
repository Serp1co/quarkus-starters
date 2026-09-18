package it.bancaditalia.quarkus.config.observability;

import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import it.bancaditalia.quarkus.platform.contract.ContractContributor;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ObservabilityPlatformKeys implements ContractContributor {

    @Override
    public void contribute(ConfigContract.Builder builder) {
        builder.platform("quarkus.management.port", "int", "9000", "Management port: health, conformance endpoint")
                .platform("quarkus.log.level", "String", "INFO", "Root log level");
    }

    @Override
    public int order() {
        return PLATFORM_ORDER;
    }
}
