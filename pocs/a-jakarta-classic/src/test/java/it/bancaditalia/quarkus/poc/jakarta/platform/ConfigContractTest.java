package it.bancaditalia.quarkus.poc.jakarta.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import it.bancaditalia.quarkus.platform.contract.ConfigContract;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Keeps the exported contract in sync with the code: the application's @ConfigMapping keys plus the
 * platform keys contributed by the bdi-config-* modules in use. The file is versioned so that a new key
 * shows up in code review, and ships inside the artifact (META-INF) so that AAP validates the rendered
 * configuration against exactly the artifact it deploys.
 */
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
        assertEquals(expected, Files.readString(CONTRACT),
                () -> CONTRACT + " is out of date with the @ConfigMapping interfaces or the starters in use; " + HOW_TO_REFRESH);
    }

    @Test
    void applicationKeysComeFirstThenThePlatformKeysOfTheStarters() {
        assertEquals("ledger.currency", contract.keys().get(0).name());
        assertTrue(contract.key("quarkus.datasource.jdbc.url").orElseThrow().required());
        assertTrue(contract.key("quarkus.datasource.password").orElseThrow().secret());
        assertTrue(contract.key("quarkus.management.port").isPresent());
    }
}
