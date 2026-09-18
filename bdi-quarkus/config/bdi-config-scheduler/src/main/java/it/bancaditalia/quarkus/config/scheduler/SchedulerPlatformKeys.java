package it.bancaditalia.quarkus.config.scheduler;

import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import it.bancaditalia.quarkus.platform.contract.ContractContributor;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SchedulerPlatformKeys implements ContractContributor {

    @Override
    public void contribute(ConfigContract.Builder builder) {
        builder.platform("quarkus.quartz.clustered", "boolean", "true", "Quartz cluster mode: true whenever more than one instance runs")
                .platform("quarkus.scheduler.enabled", "boolean", "true", "Switch off the scheduler on instances that must not run jobs (e.g. a read-only replica)");
    }

    @Override
    public int order() {
        return PLATFORM_ORDER;
    }
}
