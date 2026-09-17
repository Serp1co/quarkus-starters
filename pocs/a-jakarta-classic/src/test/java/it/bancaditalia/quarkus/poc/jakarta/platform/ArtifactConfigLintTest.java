package it.bancaditalia.quarkus.poc.jakarta.platform;

import it.bancaditalia.quarkus.platform.contract.ArtifactConfigLint;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Build once, configure per environment (design note 2.1): the artifact must not carry environment keys. */
class ArtifactConfigLintTest {

    @Test
    void theArtifactCarriesNoEnvironmentSpecificKeys() throws IOException {
        ArtifactConfigLint.assertNoBannedProfileKeys(Path.of("src/main/resources/application.properties"));
    }
}
