package it.bancaditalia.quarkus.poc.kafka.platform;

import it.bancaditalia.quarkus.platform.contract.ArtifactConfigLint;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ArtifactConfigLintTest {

    @Test
    void theArtifactCarriesNoEnvironmentSpecificKeys() throws IOException {
        ArtifactConfigLint.assertNoBannedProfileKeys(Path.of("src/main/resources/application.yaml"));
    }
}
