package it.bancaditalia.quarkus.platform.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactConfigLintTest {

    @Test
    void flagsEveryProfileButDevAndTest(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("application.properties");
        Files.writeString(file, """
                # %prod.commented=out
                quarkus.management.enabled=true
                %dev.quarkus.log.level=DEBUG
                %dev,test.ledger.transfer.max-amount=10
                %prod.quarkus.datasource.jdbc.url=jdbc:postgresql://db/prod
                %uat.ledger.currency=EUR
                """);
        assertEquals(List.of(
                "5: %prod.quarkus.datasource.jdbc.url=jdbc:postgresql://db/prod",
                "6: %uat.ledger.currency=EUR"),
                ArtifactConfigLint.findBannedProfileKeys(file));
        AssertionError error = assertThrows(AssertionError.class,
                () -> ArtifactConfigLint.assertNoBannedProfileKeys(file));
        assertTrue(error.getMessage().contains("%uat.ledger.currency=EUR"));
    }

    @Test
    void acceptsInnerLoopOnlyFiles(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("application.properties");
        Files.writeString(file, "%dev.a=1\n%test.b=2\nc=3\n");
        ArtifactConfigLint.assertNoBannedProfileKeys(file);
    }
}
