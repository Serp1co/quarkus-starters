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
    void flagsEveryProfileSectionButDevAndTestInYaml(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("application.yaml");
        Files.writeString(file, """
                # "%prod": commented out
                quarkus:
                  management:
                    enabled: true
                "%dev":
                  quarkus:
                    log:
                      level: DEBUG
                "%dev,test":
                  ledger:
                    transfer:
                      max-amount: "10"
                "%prod":
                  quarkus:
                    datasource:
                      jdbc:
                        url: jdbc:postgresql://db/prod
                '%uat':
                  ledger:
                    currency: EUR
                """);
        assertEquals(List.of("13: \"%prod\":", "18: '%uat':"), ArtifactConfigLint.findBannedProfileKeys(file));
        AssertionError error = assertThrows(AssertionError.class,
                () -> ArtifactConfigLint.assertNoBannedProfileKeys(file));
        assertTrue(error.getMessage().contains("'%uat':"));
    }

    @Test
    void stillUnderstandsPropertiesFiles(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("application.properties");
        Files.writeString(file, "%dev.a=1\n%dev,test.b=2\n%prod.c=3\nd=4\n");
        assertEquals(List.of("3: %prod.c=3"), ArtifactConfigLint.findBannedProfileKeys(file));
    }

    @Test
    void acceptsInnerLoopOnlyFiles(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("application.yaml");
        Files.writeString(file, "\"%dev\":\n  a: 1\n\"%test\":\n  b: 2\nc: 3\n");
        ArtifactConfigLint.assertNoBannedProfileKeys(file);
    }
}
