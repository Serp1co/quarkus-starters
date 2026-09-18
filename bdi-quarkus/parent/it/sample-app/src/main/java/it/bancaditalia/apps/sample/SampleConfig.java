package it.bancaditalia.apps.sample;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import it.bancaditalia.quarkus.platform.contract.Doc;

@ConfigMapping(prefix = "sample")
public interface SampleConfig {

    @WithDefault("Buongiorno")
    @Doc("What the endpoint answers")
    String greeting();
}
