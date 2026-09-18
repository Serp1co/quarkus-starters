package it.bancaditalia.quarkus.config.rest;

import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import it.bancaditalia.quarkus.platform.contract.ContractContributor;
import jakarta.enterprise.context.ApplicationScoped;

/** The REST stack's platform-owned keys, contributed to the application's config contract. */
@ApplicationScoped
public class RestPlatformKeys implements ContractContributor {

    @Override
    public void contribute(ConfigContract.Builder builder) {
        builder.platform("quarkus.http.port", "int", "8080", "API port, the load balancer target");
    }

    @Override
    public int order() {
        return PLATFORM_ORDER;
    }
}
