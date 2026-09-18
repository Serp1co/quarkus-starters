package it.bancaditalia.quarkus.poc.kafka.platform;

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

    @Inject
    ConfigContract contract;

    @Test
    void contractFileMatchesTheCode() throws IOException {
        String expected = contract.toJson();
        if (Boolean.getBoolean("config-contract.update")) {
            Files.createDirectories(CONTRACT.getParent());
            Files.writeString(CONTRACT, expected);
        }
        assertTrue(Files.exists(CONTRACT), "regenerate with -Dconfig-contract.update=true");
        assertEquals(expected, Files.readString(CONTRACT), "regenerate with -Dconfig-contract.update=true");
        assertTrue(contract.key("kafka.bootstrap.servers").orElseThrow().required());
        assertTrue(contract.key("mp.messaging.incoming.payments-in.group.id").isPresent());
    }
}
