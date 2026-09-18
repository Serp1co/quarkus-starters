package it.bancaditalia.quarkus.config.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.net.URI;
import org.junit.jupiter.api.Test;

class BdiDefaultsConfigSourceFactoryTest {

    @Test
    void loadsThisModulesDefaultsWithTheModuleNameAndOrdinal() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new BdiDefaultsConfigSourceFactory())
                .build();
        assertEquals("false", config.getConfigValue("quarkus.banner.enabled").getValue());
        assertEquals("BdiDefaults[bdi-config-core]", config.getConfigValue("quarkus.banner.enabled").getSourceName());
        assertEquals(BdiDefaultsConfigSourceFactory.ORDINAL,
                config.getConfigValue("quarkus.banner.enabled").getSourceOrdinal());
    }

    @Test
    void derivesTheModuleNameFromJarsAndClassDirectories() throws Exception {
        assertEquals("bdi-config-jpa", BdiDefaultsConfigSourceFactory.moduleName(
                URI.create("jar:file:/home/x/.m2/it/bancaditalia/bdi-config-jpa-1.0.0-SNAPSHOT.jar!/META-INF/bdi-defaults.yaml").toURL()));
        assertEquals("bdi-config-rest", BdiDefaultsConfigSourceFactory.moduleName(
                URI.create("file:/work/bdi-quarkus/config/bdi-config-rest/target/classes/META-INF/bdi-defaults.yaml").toURL()));
    }
}
