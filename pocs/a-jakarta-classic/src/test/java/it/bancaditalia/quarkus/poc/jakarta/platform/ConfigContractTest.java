package it.bancaditalia.quarkus.poc.jakarta.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Keeps the exported contract in sync with the code. The file is versioned so that a new key shows up in
 * code review, and ships inside the artifact (META-INF) so that AAP validates the rendered properties
 * against exactly the artifact it deploys.
 */
class ConfigContractTest {

    static final Path CONTRACT = Path.of("src/main/resources/META-INF/config-contract.json");
    static final String HOW_TO_REFRESH = "regenerate it with: ./mvnw test -Dconfig-contract.update=true -Dtest=ConfigContractTest -Dsurefire.failIfNoSpecifiedTests=false";

    @Test
    void contractFileMatchesTheCode() throws IOException {
        String expected = PlatformContract.define().toJson();
        if (Boolean.getBoolean("config-contract.update")) {
            Files.createDirectories(CONTRACT.getParent());
            Files.writeString(CONTRACT, expected);
        }
        assertTrue(Files.exists(CONTRACT), () -> CONTRACT + " is missing; " + HOW_TO_REFRESH);
        assertEquals(expected, Files.readString(CONTRACT),
                () -> CONTRACT + " is out of date with the @ConfigMapping interfaces; " + HOW_TO_REFRESH);
    }
}
