package it.bancaditalia.quarkus.poc.remote.caller.config;

import io.smallrye.config.ConfigMapping;
import it.bancaditalia.quarkus.platform.contract.Doc;

@ConfigMapping(prefix = "reports")
public interface ReportsConfig {

    @Doc("Name of the reporting unit, stamped on every report; set per environment")
    String unit();
}
