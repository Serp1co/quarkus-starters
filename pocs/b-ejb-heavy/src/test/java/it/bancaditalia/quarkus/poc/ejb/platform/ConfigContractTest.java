package it.bancaditalia.quarkus.poc.ejb.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConfigContractTest {

    static final Path CONTRACT = Path.of("src/main/resources/META-INF/config-contract.json");
    static final String HOW_TO_REFRESH = "regenerate it with: ./mvnw test -Dconfig-contract.update=true -Dtest=ConfigContractTest -Dsurefire.failIfNoSpecifiedTests=false";

    @Inject
    ConfigContract contract;

    @Test
    void contractFileMatchesTheCode() throws IOException {
        String expected = contract.toJson();
        if (Boolean.getBoolean("config-contract.update")) {
            Files.createDirectories(CONTRACT.getParent());
            Files.writeString(CONTRACT, expected);
        }
        assertTrue(Files.exists(CONTRACT), () -> CONTRACT + " is missing; " + HOW_TO_REFRESH);
        assertEquals(expected, Files.readString(CONTRACT), () -> CONTRACT + " is out of date; " + HOW_TO_REFRESH);
    }

    @Test
    void classBKeysArePartOfTheContract() {
        assertTrue(contract.key("settlement.max-instruction-amount").orElseThrow().required());
        assertTrue(contract.key("quarkus.transaction-manager.node-name").isPresent());
        assertTrue(contract.key("quarkus.transaction-manager.object-store.directory").isPresent());
        assertTrue(contract.key("quarkus.quartz.clustered").isPresent());
        assertTrue(contract.key("quarkus.flyway.migrate-at-start").isPresent());
    }
}
