package it.bancaditalia.quarkus.config.flyway;

import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import it.bancaditalia.quarkus.platform.contract.ContractContributor;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FlywayPlatformKeys implements ContractContributor {

    @Override
    public void contribute(ConfigContract.Builder builder) {
        builder.platform("quarkus.flyway.migrate-at-start", "boolean", "true", "Run the migrations of the release at start-up (false where the DBA applies them)");
    }

    @Override
    public int order() {
        return PLATFORM_ORDER;
    }
}
