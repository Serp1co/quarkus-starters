package it.bancaditalia.quarkus.config.observability;

import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.jboss.logging.Logger;

/**
 * Loads the contract the build derived ({@code META-INF/config-contract.json}, written by the exporter the parent
 * POM runs) for the conformance endpoint. An artifact built without it gets an empty contract and a warning.
 */
@ApplicationScoped
public class ContractLoader {

    private static final Logger LOG = Logger.getLogger(ContractLoader.class);

    @Produces
    @Singleton
    ConfigContract contract() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream("META-INF/config-contract.json")) {
            if (in == null) {
                LOG.warn("no META-INF/config-contract.json in the artifact: the build did not run the contract exporter (bdi.contract.skip?)");
                return ConfigContract.builder().build();
            }
            return ConfigContract.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
