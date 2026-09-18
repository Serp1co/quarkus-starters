package it.bancaditalia.quarkus.poc.ejb.config;

import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import it.bancaditalia.quarkus.platform.contract.ContractContributor;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SettlementContract implements ContractContributor {

    @Override
    public void contribute(ConfigContract.Builder builder) {
        builder.mapping(SettlementConfig.class);
    }
}
