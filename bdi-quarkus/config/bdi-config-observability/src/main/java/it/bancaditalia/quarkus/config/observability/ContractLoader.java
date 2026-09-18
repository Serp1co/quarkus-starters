package it.bancaditalia.quarkus.config.observability;

import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import org.jboss.logging.Logger;

/**
 * Loads the contract the build derived ({@code META-INF/config-contract.json}, written by the exporter of the
 * platform parent POM) for the conformance endpoint, with its revision (SHA-256 of the file as packaged, the same
 * hash the deploy role computes on the file it reads out of the artifact) and the platform layer's version.
 * An artifact built without the file gets an empty contract and a warning.
 */
@ApplicationScoped
public class ContractLoader {

    private static final Logger LOG = Logger.getLogger(ContractLoader.class);

    /** The contract, the hash of its packaged JSON, and the bdi-quarkus version the artifact was built with. */
    public record ContractInfo(ConfigContract contract, String revision, String platformVersion) {
    }

    @Produces
    @Singleton
    ContractInfo info() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        String platformVersion = "unknown";
        try (InputStream in = loader.getResourceAsStream("META-INF/bdi-platform.properties")) {
            if (in != null) {
                Properties properties = new Properties();
                properties.load(in);
                platformVersion = properties.getProperty("version", platformVersion);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (InputStream in = loader.getResourceAsStream("META-INF/config-contract.json")) {
            if (in == null) {
                LOG.warn("no META-INF/config-contract.json in the artifact: the build did not run the contract exporter (bdi-quarkus-parent)");
                return new ContractInfo(ConfigContract.builder().build(), "", platformVersion);
            }
            byte[] bytes = in.readAllBytes();
            return new ContractInfo(ConfigContract.fromJson(new String(bytes, StandardCharsets.UTF_8)), sha256(bytes), platformVersion);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Produces
    @Singleton
    ConfigContract contract(ContractInfo info) {
        return info.contract();
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
